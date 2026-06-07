from __future__ import annotations

import json
import os
import signal
import threading
from typing import Any

from confluent_kafka import Consumer, KafkaError, Producer
from fastapi import HTTPException
from pydantic import ValidationError

from StockfishEngineAPI import BestMoveRequest, EvaluateRequest, manager


REQUEST_TOPIC = os.getenv("KAFKA_TOPIC_STOCKFISH_REQUESTS", "stockfish-engine-requests")
RESPONSE_TOPIC = os.getenv("KAFKA_TOPIC_STOCKFISH_RESPONSES", "stockfish-engine-responses")
BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
GROUP_ID = os.getenv("KAFKA_STOCKFISH_WORKER_GROUP_ID", "alu-chess-stockfish-workers")

_stop = threading.Event()


def _to_jsonable(value: Any) -> Any:
	if hasattr(value, "model_dump"):
		return value.model_dump(by_alias=True)
	return value


def _error_body(kind: str, message: str) -> dict[str, str]:
	return {"error": kind, "message": message}


def handle_request(request: dict[str, Any]) -> dict[str, Any]:
	request_id = str(request.get("requestId", ""))
	client_id = str(request.get("clientId", ""))
	operation = str(request.get("operation", ""))
	payload = request.get("payload") or {}

	try:
		if operation == "health":
			body = manager.health()
			return _response(request_id, client_id, True, 200, body)

		if operation == "best-move":
			body = _to_jsonable(manager.best_move(BestMoveRequest(**payload)))
			return _response(request_id, client_id, True, 200, body)

		if operation == "evaluate":
			body = _to_jsonable(manager.evaluate(EvaluateRequest(**payload)))
			return _response(request_id, client_id, True, 200, body)

		return _response(
			request_id,
			client_id,
			False,
			400,
			_error_body("UnknownOperation", f"Unsupported operation: {operation}"),
		)
	except ValidationError as ex:
		return _response(request_id, client_id, False, 400, _error_body("InvalidRequest", str(ex)))
	except HTTPException as ex:
		return _response(
			request_id,
			client_id,
			False,
			int(ex.status_code),
			_error_body("StockfishError", str(ex.detail)),
		)
	except Exception as ex:
		return _response(request_id, client_id, False, 500, _error_body("StockfishWorkerError", str(ex)))


def _response(request_id: str, client_id: str, ok: bool, status: int, body: dict[str, Any]) -> dict[str, Any]:
	return {
		"requestId": request_id,
		"clientId": client_id,
		"ok": ok,
		"status": status,
		"body": body,
	}


def _consumer() -> Consumer:
	return Consumer(
		{
			"bootstrap.servers": BOOTSTRAP_SERVERS,
			"group.id": GROUP_ID,
			"auto.offset.reset": "earliest",
			"enable.auto.commit": True,
		}
	)


def _producer() -> Producer:
	return Producer({"bootstrap.servers": BOOTSTRAP_SERVERS})


def _install_signal_handlers() -> None:
	def stop(_signum, _frame) -> None:
		_stop.set()

	signal.signal(signal.SIGINT, stop)
	signal.signal(signal.SIGTERM, stop)


def main() -> None:
	_install_signal_handlers()
	manager.start()
	consumer = _consumer()
	producer = _producer()
	consumer.subscribe([REQUEST_TOPIC])
	print(
		f"Stockfish Kafka worker listening on '{REQUEST_TOPIC}' and publishing to '{RESPONSE_TOPIC}' "
		f"({BOOTSTRAP_SERVERS})",
		flush=True,
	)

	try:
		while not _stop.is_set():
			msg = consumer.poll(1.0)
			if msg is None:
				continue
			if msg.error():
				if msg.error().code() != KafkaError._PARTITION_EOF:
					print(f"Kafka consumer error: {msg.error()}", flush=True)
				continue

			try:
				request = json.loads(msg.value().decode("utf-8"))
				response = handle_request(request)
			except Exception as ex:
				response = _response("", "", False, 500, _error_body("InvalidKafkaMessage", str(ex)))

			key = response.get("clientId") or response.get("requestId") or None
			producer.produce(
				RESPONSE_TOPIC,
				key=str(key).encode("utf-8") if key else None,
				value=json.dumps(response, separators=(",", ":")).encode("utf-8"),
			)
			producer.poll(0)
	finally:
		consumer.close()
		producer.flush(10)
		manager.stop()


if __name__ == "__main__":
	main()
