from datetime import date

import httpx
import pytest

import alerts
from models import Notification

TODAY = date(2026, 10, 16)

PAYLOAD = {
    "days": 30,
    "lowStock": [{"id": 1, "name": "Ibuprofeno 400mg", "stock": 2, "minStock": 5}],
    "expiringSoon": [
        {"id": 2, "name": "Amoxicilina", "stock": 40, "expirationDate": "2026-10-20"},
        {"id": 3, "name": "Jarabe", "stock": 10, "expirationDate": "2026-10-01"},
    ],
}


class FakeResponse:
    def __init__(self, payload, error=None):
        self._payload = payload
        self._error = error

    def raise_for_status(self):
        if self._error:
            raise self._error

    def json(self):
        return self._payload


def test_process_alerts_creates_admin_notifications(db):
    created = alerts.process_alerts(PAYLOAD, db, today=TODAY)

    assert created == 3
    messages = [n.message for n in db.query(Notification).order_by(Notification.id)]
    assert messages[0] == "Stock bajo: Ibuprofeno 400mg (quedan 2, mínimo 5)."
    assert "vence en 4 días" in messages[1]
    assert "venció hace 15 días" in messages[2]
    assert all(n.audience == "ADMIN" for n in db.query(Notification))


def test_process_alerts_does_not_repeat_within_the_same_day(db):
    alerts.process_alerts(PAYLOAD, db, today=TODAY)

    assert alerts.process_alerts(PAYLOAD, db, today=TODAY) == 0
    assert db.query(Notification).count() == 3


def test_process_alerts_reminds_again_the_next_day(db):
    alerts.process_alerts(PAYLOAD, db, today=TODAY)

    assert alerts.process_alerts(PAYLOAD, db, today=date(2026, 10, 17)) == 3


def test_process_alerts_with_empty_payload(db):
    assert alerts.process_alerts({}, db, today=TODAY) == 0


def test_fetch_alerts_calls_product_service_as_admin(monkeypatch):
    captured = {}

    def fake_get(url, params=None, headers=None, timeout=None):
        captured.update(url=url, params=params, headers=headers)
        return FakeResponse(PAYLOAD)

    monkeypatch.setenv("PRODUCT_SERVICE_URL", "http://productos:9999")
    monkeypatch.setenv("ALERT_DAYS_AHEAD", "15")
    monkeypatch.setattr(alerts.httpx, "get", fake_get)

    assert alerts.fetch_alerts() == PAYLOAD
    assert captured["url"] == "http://productos:9999/products/alerts"
    assert captured["params"] == {"days": 15}
    assert captured["headers"] == {"X-User-Role": "ADMIN"}


def test_run_alerts_once_stores_notifications(db, monkeypatch):
    monkeypatch.setattr(alerts, "fetch_alerts", lambda: PAYLOAD)

    assert alerts.run_alerts_once() == 3
    assert db.query(Notification).count() == 3


def test_run_alerts_once_survives_product_service_being_down(db, monkeypatch):
    def down():
        raise httpx.ConnectError("sin conexión")

    monkeypatch.setattr(alerts, "fetch_alerts", down)

    assert alerts.run_alerts_once() == 0
    assert db.query(Notification).count() == 0


def test_fetch_alerts_raises_on_http_error(monkeypatch):
    error = httpx.HTTPStatusError("500", request=httpx.Request("GET", "http://x"), response=httpx.Response(500))
    monkeypatch.setattr(alerts.httpx, "get", lambda *a, **k: FakeResponse({}, error=error))

    with pytest.raises(httpx.HTTPStatusError):
        alerts.fetch_alerts()
