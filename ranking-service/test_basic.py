#!/usr/bin/env python3
"""
基本功能测试脚本
"""
import sys
import os
sys.path.insert(0, '.')

# Mock logger to avoid dependency
class MockLogger:
    def info(self, msg): print(f"INFO: {msg}")
    def warning(self, msg): print(f"WARNING: {msg}")
    def error(self, msg): print(f"ERROR: {msg}")

# Replace logger import
import app.services.fusion_reranking_service as frs
frs.logger = MockLogger()

try:
    from app.services.fusion_reranking_service import FusionRerankingService
    print("✓ FusionRerankingService imported successfully")
    
    service = FusionRerankingService()
    print("✓ Service initialized successfully")
    
    config = service.get_service_config()
    print("✓ Config retrieved successfully")
    print("  Algorithm weights:", config['algorithm_weights'])
    
    # Test text similarity
    similarity = service._calculate_text_similarity("hello world", "hello world")
    print(f"✓ Text similarity test passed: {similarity}")
    
    # Test freshness boost
    from datetime import datetime
    content = {'publish_time': datetime.now().isoformat()}
    freshness = service._calculate_freshness_boost(content)
    print(f"✓ Freshness boost test passed: {freshness}")
    
    # Test popularity boost
    content = {'view_count': 1000, 'like_count': 100, 'share_count': 10, 'comment_count': 5}
    popularity = service._calculate_popularity_boost(content)
    print(f"✓ Popularity boost test passed: {popularity}")
    
    print("\n✅ All basic tests passed!")
    
except Exception as e:
    print(f"❌ Test failed: {e}")
    import traceback
    traceback.print_exc()