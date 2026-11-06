import database


def test_prefers_database_url(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "mysql+pymysql://u:p@h:3306/x")
    monkeypatch.setenv("DB_HOST", "ignorado")

    assert database.build_database_url() == "mysql+pymysql://u:p@h:3306/x"


def test_builds_mysql_url_from_db_variables(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.setenv("DB_HOST", "notification-db")
    monkeypatch.setenv("DB_PORT", "3310")
    monkeypatch.setenv("DB_USER", "app")
    monkeypatch.setenv("DB_PASSWORD", "secreto")
    monkeypatch.setenv("DB_NAME", "notificaciones")

    assert database.build_database_url() == (
        "mysql+pymysql://app:secreto@notification-db:3310/notificaciones?charset=utf8mb4"
    )


def test_falls_back_to_local_sqlite(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.delenv("DB_HOST", raising=False)

    assert database.build_database_url() == "sqlite:///./notifications.db"


def test_get_db_yields_and_closes_a_session():
    generator = database.get_db()
    session = next(generator)

    assert session is not None
    generator.close()
