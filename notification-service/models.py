from sqlalchemy import BigInteger, Column, DateTime, Integer, String, Text
from sqlalchemy.sql import func

from database import Base

# Tipos de notificación
ORDER_STATUS = "ORDER_STATUS"  # para el cliente: cambió el estado de su pedido
NEW_ORDER = "NEW_ORDER"        # para la botica: llegó una nueva reserva
LOW_STOCK = "LOW_STOCK"        # para la botica: stock bajo
EXPIRING = "EXPIRING"          # para la botica: producto vencido o por vencer

AUDIENCE_USER = "USER"
AUDIENCE_ADMIN = "ADMIN"


class Notification(Base):
    __tablename__ = "notifications"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    audience = Column(String(10), nullable=False, default=AUDIENCE_USER, index=True)
    user_id = Column(BigInteger, nullable=True, index=True)
    type = Column(String(30), nullable=False)
    message = Column(Text, nullable=False)
    purchase_id = Column(BigInteger, nullable=True)
    product_id = Column(BigInteger, nullable=True)
    # Evita duplicados (reentregas de Kafka, alertas repetidas el mismo día)
    dedupe_key = Column(String(120), nullable=True, unique=True)
    created_at = Column(DateTime, server_default=func.now())

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "audience": self.audience,
            "userId": self.user_id,
            "type": self.type,
            "message": self.message,
            "purchaseId": self.purchase_id,
            "productId": self.product_id,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
        }
