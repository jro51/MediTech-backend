import os

# Deben definirse ANTES de importar la app: usa SQLite en memoria y sin hilos de fondo.
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["DISABLE_BACKGROUND_TASKS"] = "1"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from database import Base, SessionLocal, engine  # noqa: E402
from main import app  # noqa: E402


@pytest.fixture(autouse=True)
def fresh_database():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    yield


@pytest.fixture
def db():
    session = SessionLocal()
    yield session
    session.close()


@pytest.fixture
def client():
    return TestClient(app)
