from __future__ import annotations

import os
import threading
from contextlib import asynccontextmanager
from typing import AsyncIterator, Optional

import chess
import chess.engine
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field


class BestMoveRequest(BaseModel):
	fen: str
	thinkTimeMs: int = Field(default=1000, ge=1, le=120000)
	skillLevel: Optional[int] = Field(default=None, ge=0, le=20)
	threads: Optional[int] = Field(default=None, ge=1, le=64)
	hashMb: Optional[int] = Field(default=None, ge=1, le=4096)


class EvaluateRequest(BaseModel):
	fen: str
	thinkTimeMs: int = Field(default=1000, ge=1, le=120000)
	skillLevel: Optional[int] = Field(default=None, ge=0, le=20)
	threads: Optional[int] = Field(default=None, ge=1, le=64)
	hashMb: Optional[int] = Field(default=None, ge=1, le=4096)


class MoveDto(BaseModel):
	model_config = ConfigDict(populate_by_name=True)

	from_square: str = Field(alias="from")
	to: str
	promotion: Optional[str] = None


class BestMoveResponse(BaseModel):
	move: MoveDto
	uci: str
	scoreCp: Optional[int] = None
	mate: Optional[int] = None
	depth: Optional[int] = None
	nodes: Optional[int] = None
	timeMs: Optional[int] = None
	engine: str = "stockfish"


class EvaluateResponse(BaseModel):
	scoreCp: Optional[int] = None
	mate: Optional[int] = None
	depth: Optional[int] = None
	nodes: Optional[int] = None
	timeMs: Optional[int] = None
	bestMove: Optional[MoveDto] = None
	bestMoveUci: Optional[str] = None
	engine: str = "stockfish"


class StockfishManager:
	def __init__(self) -> None:
		self.engine_path = os.getenv("STOCKFISH_PATH", "/usr/games/stockfish")
		self._engine: Optional[chess.engine.SimpleEngine] = None
		self._startup_error: Optional[str] = None
		self._lock = threading.Lock()

	def start(self) -> None:
		with self._lock:
			self._start_locked()

	def stop(self) -> None:
		with self._lock:
			if self._engine is not None:
				self._engine.quit()
			self._engine = None

	def _start_locked(self) -> None:
		if self._engine is not None:
			return
		try:
			self._engine = chess.engine.SimpleEngine.popen_uci(self.engine_path)
			self._startup_error = None
		except Exception as ex:  # pragma: no cover - startup can fail due to host env
			self._engine = None
			self._startup_error = str(ex)

	def ensure_engine(self) -> chess.engine.SimpleEngine:
		if self._engine is None:
			self._start_locked()
		if self._engine is None:
			detail = self._startup_error or "Stockfish could not be started"
			raise HTTPException(status_code=503, detail=detail)
		return self._engine

	def configure(self, req: BestMoveRequest | EvaluateRequest) -> None:
		engine = self.ensure_engine()
		options = {}
		if req.skillLevel is not None:
			options["Skill Level"] = req.skillLevel
		if req.threads is not None:
			options["Threads"] = req.threads
		if req.hashMb is not None:
			options["Hash"] = req.hashMb
		if options:
			engine.configure(options)

	def health(self) -> dict:
		with self._lock:
			self.ensure_engine()
			return {
				"status": "ok",
				"service": "stockfish-engine",
				"enginePath": self.engine_path,
			}

	def best_move(self, req: BestMoveRequest) -> BestMoveResponse:
		board = parse_fen(req.fen)
		with self._lock:
			try:
				engine = self.ensure_engine()
				self.configure(req)
				result = engine.play(
					board,
					chess.engine.Limit(time=req.thinkTimeMs / 1000.0),
					info=chess.engine.INFO_BASIC | chess.engine.INFO_SCORE,
				)
			except chess.engine.EngineTerminatedError as ex:
				self._engine = None
				raise HTTPException(status_code=503, detail=f"Stockfish terminated: {ex}") from ex
			except TimeoutError as ex:
				raise HTTPException(status_code=504, detail="Stockfish request timed out") from ex
			except Exception as ex:
				raise HTTPException(status_code=500, detail=f"Stockfish error: {ex}") from ex

		if result.move is None:
			raise HTTPException(status_code=422, detail="No legal move available")

		info = result.info or {}
		score_cp, mate = extract_score(info.get("score"), board.turn)
		move = to_move_dto(result.move)

		return BestMoveResponse(
			move=move,
			uci=result.move.uci(),
			scoreCp=score_cp,
			mate=mate,
			depth=to_optional_int(info.get("depth")),
			nodes=to_optional_int(info.get("nodes")),
			timeMs=to_optional_time_ms(info.get("time")),
		)

	def evaluate(self, req: EvaluateRequest) -> EvaluateResponse:
		board = parse_fen(req.fen)
		with self._lock:
			try:
				engine = self.ensure_engine()
				self.configure(req)
				info = engine.analyse(
					board,
					chess.engine.Limit(time=req.thinkTimeMs / 1000.0),
					info=chess.engine.INFO_BASIC | chess.engine.INFO_SCORE | chess.engine.INFO_PV,
				)
			except chess.engine.EngineTerminatedError as ex:
				self._engine = None
				raise HTTPException(status_code=503, detail=f"Stockfish terminated: {ex}") from ex
			except TimeoutError as ex:
				raise HTTPException(status_code=504, detail="Stockfish request timed out") from ex
			except Exception as ex:
				raise HTTPException(status_code=500, detail=f"Stockfish error: {ex}") from ex

		score_cp, mate = extract_score(info.get("score"), board.turn)
		pv = info.get("pv") or []
		first_move = pv[0] if pv else None

		return EvaluateResponse(
			scoreCp=score_cp,
			mate=mate,
			depth=to_optional_int(info.get("depth")),
			nodes=to_optional_int(info.get("nodes")),
			timeMs=to_optional_time_ms(info.get("time")),
			bestMove=to_move_dto(first_move) if first_move else None,
			bestMoveUci=first_move.uci() if first_move else None,
		)


def parse_fen(fen: str) -> chess.Board:
	try:
		return chess.Board(fen)
	except ValueError as ex:
		raise HTTPException(status_code=400, detail=f"Invalid FEN: {ex}") from ex


def to_move_dto(move: chess.Move) -> MoveDto:
	promotion = chess.piece_symbol(move.promotion).upper() if move.promotion else None
	return MoveDto(
		**{
			"from": chess.square_name(move.from_square),
			"to": chess.square_name(move.to_square),
			"promotion": promotion,
		}
	)


def extract_score(
	score: Optional[chess.engine.PovScore],
	side_to_move: chess.Color,
) -> tuple[Optional[int], Optional[int]]:
	if score is None:
		return None, None
	pov_score = score.pov(side_to_move)
	mate = pov_score.mate()
	if mate is not None:
		return None, int(mate)
	cp = pov_score.score()
	return (int(cp), None) if cp is not None else (None, None)


def to_optional_int(value: object) -> Optional[int]:
	if value is None:
		return None
	try:
		return int(value)
	except (TypeError, ValueError):
		return None


def to_optional_time_ms(value: object) -> Optional[int]:
	if value is None:
		return None
	try:
		return int(float(value) * 1000.0)
	except (TypeError, ValueError):
		return None


manager = StockfishManager()


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
	manager.start()
	try:
		yield
	finally:
		manager.stop()


app = FastAPI(title="Stockfish Engine API", version="1.0.0", lifespan=lifespan)


@app.middleware("http")
async def set_content_type(request, call_next):
	response = await call_next(request)
	response.headers["Content-Type"] = "application/json; charset=utf-8"
	return response


@app.get("/health")
def health() -> dict:
	return manager.health()


@app.post("/best-move", response_model=BestMoveResponse)
def best_move(req: BestMoveRequest) -> BestMoveResponse:
	return manager.best_move(req)


@app.post("/evaluate", response_model=EvaluateResponse)
def evaluate(req: EvaluateRequest) -> EvaluateResponse:
	return manager.evaluate(req)
