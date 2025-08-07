#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
性能目标验证脚本
验证系统是否达到性能目标：10000 QPS、500ms响应时间、99.9%可用性
"""

import sys
import json
import csv
import statistics
import requests
import time
import threading
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

class PerformanceValidator:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url
        self.performance_targets = {
            'qps': 10000,
            'response_time_p95': 500,  # ms
            'availability': 99.9       # %
        }
        self.test_results = []
        self.validation_results = {}
    
    def validate_service_availability(self):
        """验证服务可用性"""
        print("🔍 验证服务可用性...")
        
        try:
            response = requests.get(f"{self.base_url}/actuator/health", timeout=5)
            if response.status_code == 200:
                health_data = response.json()
                service_status = health_data.get('status', 'DOWN')
                print(f"✅ 服务状态: {service_status}")
                return service_status == 'UP'
            else:
                print(f"❌ 服务健康检查失败: HTTP {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 服务连接失败: {e}")
            return False
    
    def run_qps_test(self, duration=60, target_qps=10000):
        """运行QPS测试"""
        print(f"🚀 开始QPS测试 (目标: {target_qps} QPS, 持续: {duration}秒)...")
        
        # 计算需要的线程数
        threads_count = min(target_qps // 10, 1000)  # 每个线程每秒10个请求
        requests_per_thread = target_qps // threads_count
        
        results = []
        start_time = time.time()
        
        def make_requests(thread_id):
            """单个线程的请求函数"""
            thread_results = []
            thread_start = time.time()
            
            while time.time() - thread_start < duration:
                request_start = time.time()
                try:
                    response = requests.get(
                        f"{self.base_url}/api/v1/recommend/content",
                        params={
                            'userId': f'user_{thread_id}_{int(time.time())}',
                            'size': 10,
                            'contentType': 'mixed'
                        },
                        timeout=5
                    )
                    
                    request_end = time.time()
                    response_time = (request_end - request_start) * 1000  # ms
                    
                    thread_results.append({
                        'timestamp': request_start,
                        'response_time': response_time,
                        'status_code': response.status_code,
                        'success': response.status_code == 200,
                        'thread_id': thread_id
                    })
                    
                    # 控制请求频率
                    time.sleep(max(0, 1.0/requests_per_thread - (request_end - request_start)))
                    
                except Exception as e:
                    request_end = time.time()
                    response_time = (request_end - request_start) * 1000
                    
                    thread_results.append({
                        'timestamp': request_start,
                        'response_time': response_time,
                        'status_code': 0,
                        'success': False,
                        'error': str(e),
                        'thread_id': thread_id
                    })
            
            return thread_results
        
        # 使用线程池执行请求
        with ThreadPoolExecutor(max_workers=threads_count) as executor:
            futures = [executor.submit(make_requests, i) for i in range(threads_count)]
            
            for future in as_completed(futures):
                try:
                    thread_results = future.result()
                    results.extend(thread_results)
                except Exception as e:
                    print(f"线程执行失败: {e}")
        
        end_time = time.time()
        actual_duration = end_time - start_time
        
        # 分析结果
        total_requests = len(results)
        successful_requests = sum(1 for r in results if r['success'])
        failed_requests = total_requests - successful_requests
        
        actual_qps = total_requests / actual_duration
        success_rate = (successful_requests / total_requests) * 100 if total_requests > 0 else 0
        
        response_times = [r['response_time'] for r in results if r['success']]
        if response_times:
            avg_response_time = statistics.mean(response_times)
            p95_response_time = self._percentile(response_times, 95)
            p99_response_time = self._percentile(response_times, 99)
        else:
            avg_response_time = p95_response_time = p99_response_time = 0
        
        qps_results = {
            'target_qps': target_qps,
            'actual_qps': round(actual_qps, 2),
            'total_requests': total_requests,
            'successful_requests': successful_requests,
            'failed_requests': failed_requests,
            'success_rate': round(success_rate, 2),
            'avg_response_time': round(avg_response_time, 2),
            'p95_response_time': round(p95_response_time, 2),
            'p99_response_time': round(p99_response_time, 2),
            'duration': round(actual_duration, 2),
            'qps_achieved': actual_qps >= target_qps * 0.95,  # 允许5%误差
            'response_time_achieved': p95_response_time <= self.performance_targets['response_time_p95'],
            'availability_achieved': success_rate >= self.performance_targets['availability']
        }
        
        self.test_results = results
        return qps_results
    
    def run_stress_test(self, max_users=2000, ramp_up_time=120):
        """运行压力测试"""
        print(f"🔥 开始压力测试 (最大用户数: {max_users}, 启动时间: {ramp_up_time}秒)...")
        
        results = []
        start_time = time.time()
        
        def stress_worker(user_id, start_delay):
            """压力测试工作线程"""
            time.sleep(start_delay)  # 延迟启动
            
            user_results = []
            worker_start = time.time()
            
            # 每个用户运行5分钟
            while time.time() - worker_start < 300:
                request_start = time.time()
                try:
                    response = requests.get(
                        f"{self.base_url}/api/v1/recommend/content",
                        params={
                            'userId': f'stress_user_{user_id}',
                            'size': 20,
                            'contentType': 'mixed'
                        },
                        timeout=10
                    )
                    
                    request_end = time.time()
                    response_time = (request_end - request_start) * 1000
                    
                    user_results.append({
                        'timestamp': request_start,
                        'response_time': response_time,
                        'status_code': response.status_code,
                        'success': response.status_code == 200,
                        'user_id': user_id
                    })
                    
                    # 模拟用户思考时间
                    time.sleep(1 + (user_id % 3))
                    
                except Exception as e:
                    request_end = time.time()
                    response_time = (request_end - request_start) * 1000
                    
                    user_results.append({
                        'timestamp': request_start,
                        'response_time': response_time,
                        'status_code': 0,
                        'success': False,
                        'error': str(e),
                        'user_id': user_id
                    })
            
            return user_results
        
        # 创建用户线程，逐步增加负载
        with ThreadPoolExecutor(max_workers=max_users) as executor:
            futures = []
            
            for user_id in range(max_users):
                # 计算启动延迟，实现渐进式加压
                start_delay = (user_id / max_users) * ramp_up_time
                future = executor.submit(stress_worker, user_id, start_delay)
                futures.append(future)
            
            # 收集结果
            for future in as_completed(futures):
                try:
                    user_results = future.result()
                    results.extend(user_results)
                except Exception as e:
                    print(f"压力测试用户线程失败: {e}")
        
        # 分析压力测试结果
        if results:
            total_requests = len(results)
            successful_requests = sum(1 for r in results if r['success'])
            success_rate = (successful_requests / total_requests) * 100
            
            response_times = [r['response_time'] for r in results if r['success']]
            if response_times:
                avg_response_time = statistics.mean(response_times)
                p95_response_time = self._percentile(response_times, 95)
                max_response_time = max(response_times)
            else:
                avg_response_time = p95_response_time = max_response_time = 0
            
            stress_results = {
                'max_concurrent_users': max_users,
                'total_requests': total_requests,
                'successful_requests': successful_requests,
                'success_rate': round(success_rate, 2),
                'avg_response_time': round(avg_response_time, 2),
                'p95_response_time': round(p95_response_time, 2),
                'max_response_time': round(max_response_time, 2),
                'system_stable': success_rate >= 95 and p95_response_time <= 1000
            }
        else:
            stress_results = {'error': '压力测试未产生有效结果'}
        
        return stress_results
    
    def validate_response_time_consistency(self, sample_size=1000):
        """验证响应时间一致性"""
        print(f"⏱️ 验证响应时间一致性 (样本数: {sample_size})...")
        
        response_times = []
        
        def measure_response_time():
            start_time = time.time()
            try:
                response = requests.get(
                    f"{self.base_url}/api/v1/recommend/content",
                    params={
                        'userId': f'consistency_test_{int(time.time())}',
                        'size': 10,
                        'contentType': 'mixed'
                    },
                    timeout=5
                )
                end_time = time.time()
                
                if response.status_code == 200:
                    return (end_time - start_time) * 1000
            except:
                pass
            return None
        
        # 使用线程池并发测试
        with ThreadPoolExecutor(max_workers=50) as executor:
            futures = [executor.submit(measure_response_time) for _ in range(sample_size)]
            
            for future in as_completed(futures):
                result = future.result()
                if result is not None:
                    response_times.append(result)
        
        if response_times:
            avg_time = statistics.mean(response_times)
            std_dev = statistics.stdev(response_times) if len(response_times) > 1 else 0
            p50_time = self._percentile(response_times, 50)
            p95_time = self._percentile(response_times, 95)
            p99_time = self._percentile(response_times, 99)
            
            # 计算变异系数 (标准差/平均值)
            coefficient_of_variation = (std_dev / avg_time) * 100 if avg_time > 0 else 0
            
            consistency_results = {
                'sample_size': len(response_times),
                'avg_response_time': round(avg_time, 2),
                'std_deviation': round(std_dev, 2),
                'coefficient_of_variation': round(coefficient_of_variation, 2),
                'p50_response_time': round(p50_time, 2),
                'p95_response_time': round(p95_time, 2),
                'p99_response_time': round(p99_time, 2),
                'consistency_good': coefficient_of_variation < 50  # 变异系数小于50%认为一致性良好
            }
        else:
            consistency_results = {'error': '无法获取有效的响应时间数据'}
        
        return consistency_results
    
    def _percentile(self, data, percentile):
        """计算百分位数"""
        if not data:
            return 0
        sorted_data = sorted(data)
        index = int((percentile / 100.0) * len(sorted_data))
        if index >= len(sorted_data):
            index = len(sorted_data) - 1
        return sorted_data[index]
    
    def validate_performance_targets(self):
        """验证所有性能目标"""
        print("🎯 开始性能目标验证...")
        print("=" * 60)
        
        validation_results = {}
        
        # 1. 验证服务可用性
        service_available = self.validate_service_availability()
        validation_results['service_availability'] = service_available
        
        if not service_available:
            print("❌ 服务不可用，无法继续性能测试")
            return validation_results
        
        # 2. 运行QPS测试
        qps_results = self.run_qps_test(duration=60, target_qps=self.performance_targets['qps'])
        validation_results['qps_test'] = qps_results
        
        # 3. 验证响应时间一致性
        consistency_results = self.validate_response_time_consistency()
        validation_results['response_time_consistency'] = consistency_results
        
        # 4. 运行压力测试
        stress_results = self.run_stress_test(max_users=1000, ramp_up_time=60)
        validation_results['stress_test'] = stress_results
        
        # 5. 综合评估
        overall_assessment = self._assess_overall_performance(validation_results)
        validation_results['overall_assessment'] = overall_assessment
        
        return validation_results
    
    def _assess_overall_performance(self, results):
        """综合评估性能"""
        qps_test = results.get('qps_test', {})
        consistency_test = results.get('response_time_consistency', {})
        stress_test = results.get('stress_test', {})
        
        # 评估各项指标
        qps_score = 100 if qps_test.get('qps_achieved', False) else 0
        response_time_score = 100 if qps_test.get('response_time_achieved', False) else 0
        availability_score = 100 if qps_test.get('availability_achieved', False) else 0
        consistency_score = 100 if consistency_test.get('consistency_good', False) else 0
        stability_score = 100 if stress_test.get('system_stable', False) else 0
        
        # 计算总分
        total_score = (qps_score + response_time_score + availability_score + consistency_score + stability_score) / 5
        
        # 确定等级
        if total_score >= 90:
            grade = 'A'
            status = '优秀'
        elif total_score >= 80:
            grade = 'B'
            status = '良好'
        elif total_score >= 70:
            grade = 'C'
            status = '一般'
        elif total_score >= 60:
            grade = 'D'
            status = '需要改进'
        else:
            grade = 'F'
            status = '不合格'
        
        return {
            'total_score': round(total_score, 1),
            'grade': grade,
            'status': status,
            'individual_scores': {
                'qps': qps_score,
                'response_time': response_time_score,
                'availability': availability_score,
                'consistency': consistency_score,
                'stability': stability_score
            },
            'targets_met': {
                'qps_target': qps_test.get('qps_achieved', False),
                'response_time_target': qps_test.get('response_time_achieved', False),
                'availability_target': qps_test.get('availability_achieved', False)
            }
        }
    
    def generate_validation_report(self, results):
        """生成验证报告"""
        print("\n" + "=" * 60)
        print("智能内容推荐平台性能验证报告")
        print("=" * 60)
        print(f"验证时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print()
        
        # QPS测试结果
        qps_test = results.get('qps_test', {})
        if qps_test:
            print("🚀 QPS测试结果")
            print("-" * 30)
            print(f"目标QPS: {qps_test.get('target_qps', 0):,}")
            print(f"实际QPS: {qps_test.get('actual_qps', 0):,}")
            print(f"QPS达成: {'✅ 是' if qps_test.get('qps_achieved', False) else '❌ 否'}")
            print(f"总请求数: {qps_test.get('total_requests', 0):,}")
            print(f"成功率: {qps_test.get('success_rate', 0)}%")
            print(f"平均响应时间: {qps_test.get('avg_response_time', 0)}ms")
            print(f"95%响应时间: {qps_test.get('p95_response_time', 0)}ms")
            print()
        
        # 响应时间一致性结果
        consistency_test = results.get('response_time_consistency', {})
        if consistency_test and 'error' not in consistency_test:
            print("⏱️ 响应时间一致性测试")
            print("-" * 30)
            print(f"样本数量: {consistency_test.get('sample_size', 0):,}")
            print(f"平均响应时间: {consistency_test.get('avg_response_time', 0)}ms")
            print(f"标准差: {consistency_test.get('std_deviation', 0)}ms")
            print(f"变异系数: {consistency_test.get('coefficient_of_variation', 0)}%")
            print(f"一致性: {'✅ 良好' if consistency_test.get('consistency_good', False) else '❌ 需改进'}")
            print()
        
        # 压力测试结果
        stress_test = results.get('stress_test', {})
        if stress_test and 'error' not in stress_test:
            print("🔥 压力测试结果")
            print("-" * 30)
            print(f"最大并发用户: {stress_test.get('max_concurrent_users', 0):,}")
            print(f"总请求数: {stress_test.get('total_requests', 0):,}")
            print(f"成功率: {stress_test.get('success_rate', 0)}%")
            print(f"平均响应时间: {stress_test.get('avg_response_time', 0)}ms")
            print(f"95%响应时间: {stress_test.get('p95_response_time', 0)}ms")
            print(f"系统稳定性: {'✅ 稳定' if stress_test.get('system_stable', False) else '❌ 不稳定'}")
            print()
        
        # 综合评估
        overall = results.get('overall_assessment', {})
        if overall:
            print("📊 综合评估")
            print("-" * 30)
            print(f"总体评分: {overall.get('total_score', 0)}/100")
            print(f"性能等级: {overall.get('grade', 'N/A')}")
            print(f"评估状态: {overall.get('status', '未知')}")
            print()
            
            print("🎯 性能目标达成情况:")
            targets = overall.get('targets_met', {})
            print(f"QPS目标 (10,000): {'✅ 达成' if targets.get('qps_target', False) else '❌ 未达成'}")
            print(f"响应时间目标 (500ms): {'✅ 达成' if targets.get('response_time_target', False) else '❌ 未达成'}")
            print(f"可用性目标 (99.9%): {'✅ 达成' if targets.get('availability_target', False) else '❌ 未达成'}")
            print()
        
        # 保存详细报告
        report_file = f"performance_validation_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        
        print(f"📄 详细验证报告已保存至: {report_file}")
        print("=" * 60)

def main():
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    
    validator = PerformanceValidator(base_url)
    results = validator.validate_performance_targets()
    validator.generate_validation_report(results)

if __name__ == '__main__':
    main()