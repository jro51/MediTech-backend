"""Alertas de inventario: consulta periódicamente a product-service y crea notificaciones
para la botica (stock bajo y productos vencidos o por vencer)."""
import logging
import os
import threading
import time
from datetime import date

import httpx

import rules
from database import SessionLocal
from models import AUDIENCE_ADMIN, EXPIRING, LOW_STOCK
from notify import add_notification

logger = logging.getLogger(__name__)


def fetch_alerts() -> dict:
    base_url = os.getenv("PRODUCT_SERVICE_URL", "http://product-service:8082")
    days = int(os.getenv("ALERT_DAYS_AHEAD", "30"))
    # Llamada interna entre servicios: se identifica como ADMIN (los endpoints internos
    # no son accesibles desde fuera porque el gateway solo expone rutas públicas autorizadas).
    response = httpx.get(
        f"{base_url}/products/alerts",
        params={"days": days},
        headers={"X-User-Role": "ADMIN"},
        timeout=10,
    )
    response.raise_for_status()
    return response.json()


def process_alerts(payload: dict, db, today: date | None = None) -> int:
    """Crea como máximo una notificación por producto y por día. Devuelve cuántas creó."""
    today = today or date.today()
    created = 0

    for product in payload.get("lowStock", []):
        created += int(
            add_notification(
                db,
                audience=AUDIENCE_ADMIN,
                type_=LOW_STOCK,
                message=rules.low_stock_message(product["name"], product["stock"], product.get("minStock")),
                product_id=product["id"],
                dedupe_key=f"LOW_STOCK:{product['id']}:{today.isoformat()}",
            )
        )

    for product in payload.get("expiringSoon", []):
        expiration = date.fromisoformat(product["expirationDate"])
        created += int(
            add_notification(
                db,
                audience=AUDIENCE_ADMIN,
                type_=EXPIRING,
                message=rules.expiration_message(product["name"], expiration, today),
                product_id=product["id"],
                dedupe_key=f"EXPIRING:{product['id']}:{today.isoformat()}",
            )
        )

    return created


def run_alerts_once() -> int:
    try:
        payload = fetch_alerts()
    except Exception as error:  # product-service caído, red, etc.: se reintenta en el próximo ciclo
        logger.warning("No se pudieron consultar las alertas de inventario: %s", error)
        return 0

    db = SessionLocal()
    try:
        created = process_alerts(payload, db)
        if created:
            logger.info("Alertas de inventario nuevas: %s", created)
        return created
    finally:
        db.close()


def alerts_loop():  # pragma: no cover - bucle infinito
    interval = int(os.getenv("ALERT_INTERVAL_SECONDS", "300"))
    while True:
        run_alerts_once()
        time.sleep(interval)


def start_alerts_thread():  # pragma: no cover
    thread = threading.Thread(target=alerts_loop, daemon=True, name="inventory-alerts")
    thread.start()
    return thread
