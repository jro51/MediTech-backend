import pytest
from sqlalchemy.exc import OperationalError
from starlette.testclient import TestClient

import main
from models import Notification


def add(db, **kwargs):
    defaults = {"audience": "USER", "type": "ORDER_STATUS", "message": "hola"}
    defaults.update(kwargs)
    db.add(Notification(**defaults))
    db.commit()


def test_health(client):
    assert client.get("/health").json() == {"status": "UP", "service": "notification-service"}


def test_user_sees_only_their_own_order_notifications(client, db):
    add(db, user_id=7, message="para 7")
    add(db, user_id=8, message="para 8")
    add(db, user_id=7, audience="ADMIN", type="LOW_STOCK", message="alerta de la botica")

    response = client.get("/notifications/7", headers={"X-User-Id": "7"})

    assert response.status_code == 200
    assert [n["message"] for n in response.json()] == ["para 7"]


def test_newest_notification_comes_first(client, db):
    add(db, user_id=7, message="primera")
    add(db, user_id=7, message="segunda")

    messages = [n["message"] for n in client.get("/notifications/7", headers={"X-User-Id": "7"}).json()]

    assert messages == ["segunda", "primera"]


def test_user_cannot_read_notifications_of_someone_else(client, db):
    add(db, user_id=8, message="privado")

    response = client.get("/notifications/8", headers={"X-User-Id": "7", "X-User-Role": "USER"})

    assert response.status_code == 403


def test_admin_can_read_notifications_of_any_user(client, db):
    add(db, user_id=8, message="de otro usuario")

    response = client.get("/notifications/8", headers={"X-User-Id": "1", "X-User-Role": "ADMIN"})

    assert response.status_code == 200
    assert len(response.json()) == 1


def test_response_uses_camel_case_fields(client, db):
    add(db, user_id=7, purchase_id=10)

    body = client.get("/notifications/7").json()[0]

    assert {"id", "audience", "userId", "type", "message", "purchaseId", "createdAt"} <= body.keys()
    assert body["purchaseId"] == 10


def test_admin_inbox_requires_admin_role(client):
    assert client.get("/notifications/admin/inbox").status_code == 403
    assert client.get("/notifications/admin/inbox", headers={"X-User-Role": "USER"}).status_code == 403


def test_admin_inbox_returns_store_notifications_only(client, db):
    add(db, audience="ADMIN", type="LOW_STOCK", message="Stock bajo: A")
    add(db, audience="ADMIN", type="NEW_ORDER", message="Nueva reserva #1")
    add(db, audience="USER", user_id=7, message="para un cliente")

    response = client.get("/notifications/admin/inbox", headers={"X-User-Role": "ADMIN"})

    assert response.status_code == 200
    assert [n["message"] for n in response.json()] == ["Nueva reserva #1", "Stock bajo: A"]


def test_admin_inbox_respects_limit(client, db):
    for i in range(5):
        add(db, audience="ADMIN", type="LOW_STOCK", message=f"alerta {i}")

    response = client.get("/notifications/admin/inbox?limit=2", headers={"X-User-Role": "ADMIN"})

    assert len(response.json()) == 2


def test_lifespan_creates_tables_on_startup():
    with TestClient(main.app) as started:
        assert started.get("/health").status_code == 200


def test_init_db_gives_up_after_retries(monkeypatch):
    def not_ready(*args, **kwargs):
        raise OperationalError("stmt", {}, Exception("MySQL todavía no está listo"))

    monkeypatch.setattr(main.Base.metadata, "create_all", not_ready)
    monkeypatch.setattr(main.time, "sleep", lambda seconds: None)

    with pytest.raises(RuntimeError):
        main.init_db(retries=3, wait_seconds=0)


def test_init_db_recovers_when_database_becomes_ready(monkeypatch):
    calls = {"count": 0}
    original = main.Base.metadata.create_all

    def flaky(*args, **kwargs):
        calls["count"] += 1
        if calls["count"] < 3:
            raise OperationalError("stmt", {}, Exception("aún no"))
        return original(*args, **kwargs)

    monkeypatch.setattr(main.Base.metadata, "create_all", flaky)
    monkeypatch.setattr(main.time, "sleep", lambda seconds: None)

    main.init_db(retries=5, wait_seconds=0)

    assert calls["count"] == 3
