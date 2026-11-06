"""Reglas de negocio para armar los mensajes. Son funciones puras: fáciles de probar.

Reemplazan a la antigua integración con IA: los mensajes ahora son deterministas
y verificables (en un dominio de salud no queremos texto generado sin revisión).
"""
from datetime import date

STATUS_MESSAGES = {
    "RESERVED": "Tu pedido #{id} fue reservado. Te avisaremos cuando esté listo para recoger.",
    "READY": "Tu pedido #{id} ya está listo: puedes pasar a recogerlo en la botica.",
    "PICKED_UP": "Gracias por recoger tu pedido #{id}.",
    "CANCELLED": "Tu pedido #{id} fue cancelado y los productos volvieron al inventario.",
}


def _days(n: int) -> str:
    return f"{n} día" if n == 1 else f"{n} días"


def order_status_message(purchase_id: int, status: str) -> str | None:
    template = STATUS_MESSAGES.get(status)
    return template.format(id=purchase_id) if template else None


def new_order_message(purchase_id: int, product_names: list[str]) -> str:
    if product_names:
        return f"Nueva reserva #{purchase_id}: {', '.join(product_names)}."
    return f"Nueva reserva #{purchase_id}."


def low_stock_message(name: str, stock: int, min_stock: int | None) -> str:
    if min_stock is None:
        return f"Stock bajo: {name} (quedan {stock})."
    return f"Stock bajo: {name} (quedan {stock}, mínimo {min_stock})."


def expiration_message(name: str, expiration_date: date, today: date) -> str:
    days_left = (expiration_date - today).days
    if days_left < 0:
        return f"Producto vencido: {name} venció hace {_days(-days_left)}."
    if days_left == 0:
        return f"Producto por vencer: {name} vence hoy."
    return f"Producto por vencer: {name} vence en {_days(days_left)}."
