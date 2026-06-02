from __future__ import annotations

import contextlib
import dataclasses
import os
import queue
import asyncio
from contextlib import asynccontextmanager
from typing import AsyncIterator, Iterator, Optional

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


@dataclasses.dataclass
class _EngineSlot:
	engine: chess.engine.SimpleEngine
	last_config: dict = dataclasses.field(default_factory=dict)


class StockfishManager:
	def __init__(self) -> None:
		self.engine_path = os.getenv("STOCKFISH_PATH", "/usr/games/stockfish")
		self._pool_size = int(os.getenv("STOCKFISH_POOL_SIZE", "2"))
		self._pool: queue.Queue[_EngineSlot] = queue.Queue()
		self._startup_error: Optional[str] = None

	def start(self) -> None:
		for _ in range(self._pool_size):
			slot = self._create_slot()
			if slot is not None:
				self._pool.put(slot)

	def stop(self) -> None:
		while not self._pool.empty():
			try:
				slot = self._pool.get_nowait()
				slot.engine.quit()
			except queue.Empty:
				break

	def _create_slot(self) -> Optional[_EngineSlot]:  # pragma: no cover - startup can fail due to host env
		try:
			engine = chess.engine.SimpleEngine.popen_uci(self.engine_path)
			return _EngineSlot(engine=engine)
		except Exception as ex:
			self._startup_error = str(ex)
			return None

	@contextlib.contextmanager
	def _acquire_slot(self) -> Iterator[_EngineSlot]:
		try:
			slot = self._pool.get(timeout=30)
		except queue.Empty:
			raise HTTPException(status_code=503, detail=self._startup_error or "No Stockfish engine available")
		replace = False
		try:
			yield slot
		except chess.engine.EngineTerminatedError:
			replace = True
			raise
		finally:
			if replace:
				new_slot = self._create_slot()
				if new_slot:
					self._pool.put(new_slot)
			else:
				self._pool.put(slot)

	def _apply_config(self, slot: _EngineSlot, req: BestMoveRequest | EvaluateRequest) -> None:
		new_config: dict = {}
		if req.skillLevel is not None:
			new_config["Skill Level"] = req.skillLevel
		if req.threads is not None:
			new_config["Threads"] = req.threads
		if req.hashMb is not None:
			new_config["Hash"] = req.hashMb
		if new_config != slot.last_config:
			slot.engine.configure(new_config)
			slot.last_config = new_config

	def health(self) -> dict:
		available = self._pool.qsize()
		if available == 0:
			raise HTTPException(status_code=503, detail=self._startup_error or "No engines available")
		return {
			"status": "ok",
			"service": "stockfish-engine",
			"enginePath": self.engine_path,
			"poolSize": self._pool_size,
			"available": available,
		}

	def best_move(self, req: BestMoveRequest) -> BestMoveResponse:
		board = parse_fen(req.fen)
		try:
			with self._acquire_slot() as slot:
				self._apply_config(slot, req)
				result = slot.engine.play(
					board,
					chess.engine.Limit(time=req.thinkTimeMs / 1000.0),
					info=chess.engine.INFO_BASIC | chess.engine.INFO_SCORE,
				)
		except chess.engine.EngineTerminatedError as ex:
			raise HTTPException(status_code=503, detail=f"Stockfish terminated: {ex}") from ex
		except TimeoutError as ex:
			raise HTTPException(status_code=504, detail="Stockfish request timed out") from ex
		except HTTPException:
			raise
		except Exception as ex:
			raise HTTPException(status_code=500, detail=f"Stockfish error: {ex}") from ex

		if result.move is None:
			raise HTTPException(status_code=422, detail="No legal move available")

		info = result.info or {}
		score_cp, mate = extract_score(info.get("score"), chess.WHITE)
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
		try:
			with self._acquire_slot() as slot:
				self._apply_config(slot, req)
				info = slot.engine.analyse(
					board,
					chess.engine.Limit(time=req.thinkTimeMs / 1000.0),
					info=chess.engine.INFO_BASIC | chess.engine.INFO_SCORE | chess.engine.INFO_PV,
				)
		except chess.engine.EngineTerminatedError as ex:
			raise HTTPException(status_code=503, detail=f"Stockfish terminated: {ex}") from ex
		except TimeoutError as ex:
			raise HTTPException(status_code=504, detail="Stockfish request timed out") from ex
		except HTTPException:
			raise
		except Exception as ex:
			raise HTTPException(status_code=500, detail=f"Stockfish error: {ex}") from ex

		score_cp, mate = extract_score(info.get("score"), chess.WHITE)
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
async def best_move(req: BestMoveRequest) -> BestMoveResponse:
	return await asyncio.to_thread(manager.best_move, req)


@app.post("/evaluate", response_model=EvaluateResponse)
async def evaluate(req: EvaluateRequest) -> EvaluateResponse:
	return await asyncio.to_thread(manager.evaluate, req)
