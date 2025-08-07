"""
排序服务主入口
"""
import uvicorn
from app.api.ranking_api import app

if __name__ == "__main__":
    uvicorn.run(
        "app.api.ranking_api:app",
        host="0.0.0.0",
        port=8002,
        reload=True,
        log_level="info"
    )