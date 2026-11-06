from datetime import date

import pytest

import rules


@pytest.mark.parametrize(
    ("status", "fragment"),
    [
        ("RESERVED", "fue reservado"),
        ("READY", "listo"),
        ("PICKED_UP", "Gracias"),
        ("CANCELLED", "cancelado"),
    ],
)
def test_order_status_message_for_each_status(status, fragment):
    message = rules.order_status_message(15, status)

    assert "#15" in message
    assert fragment in message


def test_order_status_message_for_unknown_status_is_none():
    assert rules.order_status_message(1, "LOQUESEA") is None


def test_new_order_message_lists_products():
    assert rules.new_order_message(3, ["Ibuprofeno", "Paracetamol"]) == "Nueva reserva #3: Ibuprofeno, Paracetamol."
    assert rules.new_order_message(3, []) == "Nueva reserva #3."


def test_low_stock_message_with_and_without_minimum():
    assert rules.low_stock_message("Ibuprofeno", 2, 5) == "Stock bajo: Ibuprofeno (quedan 2, mínimo 5)."
    assert rules.low_stock_message("Ibuprofeno", 2, None) == "Stock bajo: Ibuprofeno (quedan 2)."


@pytest.mark.parametrize(
    ("expiration", "expected"),
    [
        (date(2026, 10, 10), "Producto vencido: Ibuprofeno venció hace 6 días."),
        (date(2026, 10, 15), "Producto vencido: Ibuprofeno venció hace 1 día."),
        (date(2026, 10, 16), "Producto por vencer: Ibuprofeno vence hoy."),
        (date(2026, 10, 17), "Producto por vencer: Ibuprofeno vence en 1 día."),
        (date(2026, 11, 1), "Producto por vencer: Ibuprofeno vence en 16 días."),
    ],
)
def test_expiration_message(expiration, expected):
    assert rules.expiration_message("Ibuprofeno", expiration, today=date(2026, 10, 16)) == expected
