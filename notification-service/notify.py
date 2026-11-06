import logging

from sqlalchemy.orm import Session

from models import Notification

logger = logging.getLogger(__name__)


def add_notification(
    db: Session,
    *,
    audience: str,
    type_: str,
    message: str,
    user_id: int | None = None,
    purchase_id: int | None = None,
    product_id: int | None = None,
    dedupe_key: str | None = None,
) -> bool:
    """Guarda una notificación. Devuelve False si ya existía una con la misma dedupe_key."""
    if dedupe_key and db.query(Notification.id).filter(Notification.dedupe_key == dedupe_key).first():
        logger.debug("Notificación duplicada ignorada: %s", dedupe_key)
        return False

    db.add(
        Notification(
            audience=audience,
            type=type_,
            message=message,
            user_id=user_id,
            purchase_id=purchase_id,
            product_id=product_id,
            dedupe_key=dedupe_key,
        )
    )
    db.commit()
    return True
