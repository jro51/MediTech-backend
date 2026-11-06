import os

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker
from sqlalchemy.pool import StaticPool

load_dotenv()


def build_database_url() -> str:
    """DATABASE_URL > variables DB_* (MySQL) > SQLite local."""
    url = os.getenv("DATABASE_URL")
    if url:
        return url

    host = os.getenv("DB_HOST")
    if host:
        user = os.getenv("DB_USER", "root")
        password = os.getenv("DB_PASSWORD", "root")
        port = os.getenv("DB_PORT", "3306")
        name = os.getenv("DB_NAME", "notification_db")
        return f"mysql+pymysql://{user}:{password}@{host}:{port}/{name}?charset=utf8mb4"

    return "sqlite:///./notifications.db"


DATABASE_URL = build_database_url()

_engine_options: dict = {}
if DATABASE_URL.startswith("sqlite"):
    _engine_options["connect_args"] = {"check_same_thread": False}
    if DATABASE_URL in ("sqlite://", "sqlite:///:memory:"):
        # Una sola conexión compartida para que la BD en memoria no desaparezca
        _engine_options["poolclass"] = StaticPool
else:
    _engine_options["pool_pre_ping"] = True

engine = create_engine(DATABASE_URL, **_engine_options)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
