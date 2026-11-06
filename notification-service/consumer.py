import json
import logging
import os
import threading

from confluent_kafka import Consumer
from dotenv import load_dotenv
from sqlalchemy.orm import Session

import rules
from database import SessionLocal
from models import AUDIENCE_ADMIN, AUDIENCE_USER, NEW_ORDER, ORDER_STATUS
from notify import add_notification

load_dotenv()

logger = logging.getLogger(__name__)


def process_purchase_event(event: dict, db: Session) -> int:
    """Convierte un evento de pedido en notificaciones. Devuelve cuántas creó."""
    try:
        purchase_id = event["purchaseId"]
        user_id = event["userId"]
    except KeyError as missing:
        logger.error("Evento inválido, falta el campo %s: %s", missing, event)
        return 0

    status = str(event.get("status") or "RESERVED").upper()
    message = rules.order_status_message(purchase_id, status)
    if message is None:
        logger.warning("Estado de pedido desconocido '%s' en el evento %s", status, purchase_id)
        return 0

    try:
        created = int(
            add_notification(
                db,
                audience=AUDIENCE_USER,
                type_=ORDER_STATUS,
                message=message,
                user_id=user_id,
                purchase_id=purchase_id,
                dedupe_key=f"ORDER:{purchase_id}:{status}:USER",
            )
        )

        # La botica también se entera cuando entra una reserva nueva
        if status == "RESERVED":
            created += int(
                add_notification(
                    db,
                    audience=AUDIENCE_ADMIN,
                    type_=NEW_ORDER,
                    message=rules.new_order_message(purchase_id, event.get("productNames") or []),
                    purchase_id=purchase_id,
                    dedupe_key=f"ORDER:{purchase_id}:{status}:ADMIN",
                )
            )

        logger.info("Pedido #%s (%s): %s notificación(es) creada(s)", purchase_id, status, created)
        return created
    except Exception:
        db.rollback()
        logger.exception("Error procesando el evento del pedido %s", purchase_id)
        return 0


def handle_message(raw_value: bytes) -> bool:
    """Procesa el contenido crudo de un mensaje de Kafka. Devuelve True si lo pudo leer."""
    try:
        event = json.loads(raw_value.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        logger.error("Mensaje de Kafka ilegible, se descarta")
        return False

    db = SessionLocal()
    try:
        process_purchase_event(event, db)
    finally:
        db.close()
    return True


def start_consumer():  # pragma: no cover - requiere un broker de Kafka real
    """Bucle del consumidor de Kafka (confluent-kafka)."""
    conf = {
        "bootstrap.servers": os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
        "group.id": os.getenv("KAFKA_GROUP_ID", "notification-group"),
        "auto.offset.reset": "earliest",
    }
    consumer = Consumer(conf)
    topic = os.getenv("KAFKA_TOPIC", "purchase-events")
    consumer.subscribe([topic])
    logger.info("Escuchando el topic %s", topic)

    try:
        while True:
            msg = consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                logger.error("Error de Kafka: %s", msg.error())
                continue
            handle_message(msg.value())
    finally:
        consumer.close()


def start_consumer_thread():  # pragma: no cover
    thread = threading.Thread(target=start_consumer, daemon=True, name="kafka-consumer")
    thread.start()
    return thread
