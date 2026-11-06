import logging
import os
import time
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from alerts import start_alerts_thread
from consumer import start_consumer_thread
from database import Base, engine, get_db
from models import AUDIENCE_ADMIN, AUDIENCE_USER, Notification

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def init_db(retries: int = 5, wait_seconds: int = 5) -> None:
    """Crea las tablas; reintenta mientras la base de datos termina de arrancar."""
    for attempt in range(retries, 0, -1):
        try:
            Base.metadata.create_all(bind=engine)
            logger.info("Tablas creadas exitosamente.")
            return
        except OperationalError:
            logger.warning("BD no lista, reintentando en %ss... (%s intentos restantes)", wait_seconds, attempt)
            time.sleep(wait_seconds)
    raise RuntimeError("No se pudo conectar a la base de datos tras varios intentos.")


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    if os.getenv("DISABLE_BACKGROUND_TASKS") != "1":  # pragma: no cover
        start_consumer_thread()
        start_alerts_thread()
    logger.info("notification-service listo")
    yield
    logger.info("Apagando notification-service...")


app = FastAPI(
    title="MediTech · Notification Service",
    description=(
        "Notificaciones por reglas (sin IA): avisos de estado de pedido para los clientes y "
        "alertas de inventario (stock bajo, productos por vencer) para la botica. "
        "En Swagger directo, los headers X-User-Id / X-User-Role simulan lo que inyecta el API Gateway."
    ),
    version="2.0.0",
    lifespan=lifespan,
)


@app.get("/notifications/admin/inbox", tags=["Notificaciones"], summary="Bandeja de la botica (solo ADMIN)")
def admin_inbox(
    limit: int = Query(50, ge=1, le=200),
    x_user_role: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """Nuevas reservas y alertas de inventario, de la más reciente a la más antigua."""
    if x_user_role != "ADMIN":
        raise HTTPException(status_code=403, detail="Solo el ADMIN puede ver esta bandeja")

    notifications = (
        db.query(Notification)
        .filter(Notification.audience == AUDIENCE_ADMIN)
        .order_by(Notification.created_at.desc(), Notification.id.desc())
        .limit(limit)
        .all()
    )
    return [n.to_dict() for n in notifications]


@app.get("/notifications/{user_id}", tags=["Notificaciones"], summary="Notificaciones de un cliente")
def get_notifications(
    user_id: int,
    x_user_id: str | None = Header(default=None),
    x_user_role: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    """Avisos sobre el estado de los pedidos de un usuario. Cada usuario solo ve los suyos."""
    if x_user_id is not None and x_user_id != str(user_id) and x_user_role != "ADMIN":
        raise HTTPException(status_code=403, detail="No puedes ver las notificaciones de otro usuario")

    notifications = (
        db.query(Notification)
        .filter(Notification.audience == AUDIENCE_USER, Notification.user_id == user_id)
        .order_by(Notification.created_at.desc(), Notification.id.desc())
        .all()
    )
    return [n.to_dict() for n in notifications]


@app.get("/health", tags=["Salud"])
def health_check():
    return {"status": "UP", "service": "notification-service"}
