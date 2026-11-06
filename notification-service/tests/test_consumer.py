import json

from consumer import handle_message, process_purchase_event
from models import Notification


def event(**overrides):
    base = {
        "purchaseId": 10,
        "userId": 7,
        "productNames": ["Ibuprofeno", "Paracetamol"],
        "status": "RESERVED",
    }
    base.update(overrides)
    return base


def test_new_reservation_notifies_the_customer_and_the_store(db):
    created = process_purchase_event(event(), db)

    assert created == 2
    user_note = db.query(Notification).filter_by(audience="USER").one()
    admin_note = db.query(Notification).filter_by(audience="ADMIN").one()
    assert user_note.user_id == 7
    assert user_note.type == "ORDER_STATUS"
    assert "#10" in user_note.message
    assert admin_note.type == "NEW_ORDER"
    assert "Ibuprofeno" in admin_note.message


def test_status_change_notifies_only_the_customer(db):
    created = process_purchase_event(event(status="READY"), db)

    assert created == 1
    assert db.query(Notification).filter_by(audience="ADMIN").count() == 0
    assert "listo" in db.query(Notification).one().message


def test_event_without_status_is_treated_as_reserved(db):
    payload = event()
    del payload["status"]

    assert process_purchase_event(payload, db) == 2


def test_redelivered_event_does_not_duplicate_notifications(db):
    process_purchase_event(event(), db)
    created_again = process_purchase_event(event(), db)

    assert created_again == 0
    assert db.query(Notification).count() == 2


def test_each_status_of_the_same_order_creates_its_own_notification(db):
    process_purchase_event(event(status="RESERVED"), db)
    process_purchase_event(event(status="READY"), db)
    process_purchase_event(event(status="PICKED_UP"), db)

    assert db.query(Notification).filter_by(audience="USER").count() == 3


def test_event_missing_required_fields_is_ignored(db):
    assert process_purchase_event({"userId": 7}, db) == 0
    assert process_purchase_event({"purchaseId": 1}, db) == 0
    assert db.query(Notification).count() == 0


def test_unknown_status_is_ignored(db):
    assert process_purchase_event(event(status="EXTRAÑO"), db) == 0
    assert db.query(Notification).count() == 0


def test_database_error_is_handled_and_rolled_back(db, monkeypatch):
    def boom(*args, **kwargs):
        raise RuntimeError("base de datos caída")

    monkeypatch.setattr("consumer.add_notification", boom)

    assert process_purchase_event(event(), db) == 0


def test_handle_message_processes_valid_json(db):
    raw = json.dumps(event(status="CANCELLED")).encode("utf-8")

    assert handle_message(raw) is True
    assert "cancelado" in db.query(Notification).one().message


def test_handle_message_discards_garbage():
    assert handle_message(b"esto no es json") is False
    assert handle_message(b"\xff\xfe\x00") is False
