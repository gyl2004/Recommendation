#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
系统瓶颈分析脚本
分析性能测试结果，识别系统瓶颈并提供优化建议
"""

import sys
import json
import psutil
import time
import subprocess
import mysql.connector
import redis
from datetime import datetime
from collections import defaultdict

class BottleneckAnalyzer:
    def __init__(self):
        self.analysis_results = {}
        self.recommendations = []
    
    def analyze_system_resources(self):
        """分析系统资源使用情况"""
        print("🔍 分析系统资源使用情况...")
        
        # CPU分析
        cpu_percent = psutil.cpu_percent(interval=1)
        cpu_count = psutil.cpu_count()
        cpu_freq = psutil.cpu_freq()
        
        # 内存分析
        memory = psutil.virtual_memory()
        swap = psutil.swap_memory()
        
        # 磁盘分析
        disk_usage = psutil.disk_usage('/')
        disk_io = psutil.disk_io_counters()
        
        # 网络分析
        network_io = psutil.net_io_counters()
        
        system_analysis = {
            'cpu': {
                'usage_percent': cpu_percent,
                'core_count': cpu_count,
                'frequency_mhz': cpu_freq.current if cpu_freq else 0,
                'bottleneck': cpu_percent > 80
            },
            'memory': {
                'total_gb': round(memory.total / (1024**3), 2),
                'used_gb': round(memory.used / (1024**3), 2),
                'usage_percent': memory.percent,
                'available_gb': round(memory.available / (1024**3), 2),
                'bottleneck': memory.percent > 85
            },
            'disk': {
                'total_gb': round(disk_usage.total / (1024**3), 2),
                'used_gb': round(disk_usage.used / (1024**3), 2),
                'usage_percent': (disk_usage.used / disk_usage.total) * 100,
                'read_mb_s': round(disk_io.read_bytes / (1024**2), 2) if disk_io else 0,
                'write_mb_s': round(disk_io.write_bytes / (1024**2), 2) if disk_io else 0,
                'bottleneck': (disk_usage.used / disk_usage.total) * 100 > 90
            },
            'network': {
                'bytes_sent_mb': round(network_io.bytes_sent / (1024**2), 2),
                'bytes_recv_mb': round(network_io.bytes_recv / (1024**2), 2),
                'packets_sent': network_io.packets_sent,
                'packets_recv': network_io.packets_recv,
                'errors': network_io.errin + network_io.errout
            }
        }
        
        self.analysis_results['system_resources'] = system_analysis
        
        # 生成系统资源优化建议
        if system_analysis['cpu']['bottleneck']:
            self.recommendations.append({
                'category': 'CPU优化',
                'priority': 'high',
                'issue': f"CPU使用率过高: {cpu_percent}%",
                'suggestions': [
                    '增加CPU核心数或升级CPU',
                    '优化应用程序算法复杂度',
                    '实现负载均衡分散CPU压力',
                    '使用异步处理减少CPU阻塞'
                ]
            })
        
        if system_analysis['memory']['bottleneck']:
            self.recommendations.append({
                'category': '内存优化',
                'priority': 'high',
                'issue': f"内存使用率过高: {memory.percent}%",
                'suggestions': [
                    '增加系统内存',
                    '优化JVM堆内存配置',
                    '实现更有效的缓存策略',
                    '检查内存泄漏问题'
                ]
            })
        
        if system_analysis['disk']['bottleneck']:
            self.recommendations.append({
                'category': '磁盘优化',
                'priority': 'medium',
                'issue': f"磁盘使用率过高: {system_analysis['disk']['usage_percent']:.1f}%",
                'suggestions': [
                    '清理不必要的文件',
                    '实现数据归档策略',
                    '使用更快的存储设备(SSD)',
                    '实现数据分区存储'
                ]
            })
    
    def analyze_jvm_performance(self):
        """分析JVM性能"""
        print("🔍 分析JVM性能...")
        
        try:
            # 获取Java进程
            java_processes = []
            for proc in psutil.process_iter(['pid', 'name', 'cmdline']):
                if 'java' in proc.info['name'].lower():
                    java_processes.append(proc.info)
            
            jvm_analysis = {}
            for proc in java_processes:
                pid = proc['pid']
                
                # 获取JVM统计信息
                try:
                    # 使用jstat获取GC信息
                    jstat_result = subprocess.run(
                        ['jstat', '-gc', str(pid)],
                        capture_output=True, text=True, timeout=10
                    )
                    
                    if jstat_result.returncode == 0:
                        lines = jstat_result.stdout.strip().split('\n')
                        if len(lines) >= 2:
                            headers = lines[0].split()
                            values = lines[1].split()
                            gc_data = dict(zip(headers, values))
                            
                            # 计算堆内存使用情况
                            heap_used = float(gc_data.get('EU', 0)) + float(gc_data.get('OU', 0))
                            heap_total = float(gc_data.get('EC', 0)) + float(gc_data.get('OC', 0))
                            heap_usage_percent = (heap_used / heap_total * 100) if heap_total > 0 else 0
                            
                            jvm_analysis[pid] = {
                                'heap_used_mb': round(heap_used / 1024, 2),
                                'heap_total_mb': round(heap_total / 1024, 2),
                                'heap_usage_percent': round(heap_usage_percent, 2),
                                'gc_count': int(gc_data.get('YGC', 0)) + int(gc_data.get('FGC', 0)),
                                'gc_time': float(gc_data.get('YGCT', 0)) + float(gc_data.get('FGCT', 0)),
                                'bottleneck': heap_usage_percent > 85
                            }
                
                except (subprocess.TimeoutExpired, subprocess.CalledProcessError, ValueError):
                    continue
            
            self.analysis_results['jvm_performance'] = jvm_analysis
            
            # 生成JVM优化建议
            for pid, analysis in jvm_analysis.items():
                if analysis['bottleneck']:
                    self.recommendations.append({
                        'category': 'JVM优化',
                        'priority': 'high',
                        'issue': f"JVM堆内存使用率过高: {analysis['heap_usage_percent']}% (PID: {pid})",
                        'suggestions': [
                            '增加JVM堆内存大小 (-Xmx参数)',
                            '优化垃圾回收器配置',
                            '检查内存泄漏问题',
                            '优化对象创建和缓存策略'
                        ]
                    })
        
        except Exception as e:
            print(f"JVM性能分析失败: {e}")
    
    def analyze_database_performance(self):
        """分析数据库性能"""
        print("🔍 分析数据库性能...")
        
        try:
            # MySQL性能分析
            mysql_config = {
                'host': 'localhost',
                'port': 3306,
                'user': 'root',
                'password': '123456',
                'database': 'recommendation_db'
            }
            
            conn = mysql.connector.connect(**mysql_config)
            cursor = conn.cursor(dictionary=True)
            
            # 获取数据库状态
            cursor.execute("SHOW STATUS LIKE 'Threads_connected'")
            threads_connected = cursor.fetchone()['Value']
            
            cursor.execute("SHOW STATUS LIKE 'Threads_running'")
            threads_running = cursor.fetchone()['Value']
            
            cursor.execute("SHOW STATUS LIKE 'Queries'")
            total_queries = cursor.fetchone()['Value']
            
            cursor.execute("SHOW STATUS LIKE 'Slow_queries'")
            slow_queries = cursor.fetchone()['Value']
            
            # 获取InnoDB状态
            cursor.execute("SHOW STATUS LIKE 'Innodb_buffer_pool_read_requests'")
            buffer_read_requests = cursor.fetchone()['Value']
            
            cursor.execute("SHOW STATUS LIKE 'Innodb_buffer_pool_reads'")
            buffer_reads = cursor.fetchone()['Value']
            
            # 计算缓冲池命中率
            buffer_hit_rate = (1 - int(buffer_reads) / int(buffer_read_requests)) * 100 if int(buffer_read_requests) > 0 else 0
            
            mysql_analysis = {
                'connections': {
                    'connected': int(threads_connected),
                    'running': int(threads_running),
                    'bottleneck': int(threads_connected) > 100
                },
                'queries': {
                    'total': int(total_queries),
                    'slow': int(slow_queries),
                    'slow_ratio': (int(slow_queries) / int(total_queries)) * 100 if int(total_queries) > 0 else 0,
                    'bottleneck': (int(slow_queries) / int(total_queries)) * 100 > 1 if int(total_queries) > 0 else False
                },
                'buffer_pool': {
                    'hit_rate': round(buffer_hit_rate, 2),
                    'bottleneck': buffer_hit_rate < 95
                }
            }
            
            cursor.close()
            conn.close()
            
            self.analysis_results['mysql_performance'] = mysql_analysis
            
            # 生成MySQL优化建议
            if mysql_analysis['connections']['bottleneck']:
                self.recommendations.append({
                    'category': 'MySQL连接优化',
                    'priority': 'high',
                    'issue': f"MySQL连接数过高: {mysql_analysis['connections']['connected']}",
                    'suggestions': [
                        '优化连接池配置',
                        '增加max_connections参数',
                        '检查连接泄漏问题',
                        '实现连接复用'
                    ]
                })
            
            if mysql_analysis['queries']['bottleneck']:
                self.recommendations.append({
                    'category': 'MySQL查询优化',
                    'priority': 'high',
                    'issue': f"慢查询比例过高: {mysql_analysis['queries']['slow_ratio']:.2f}%",
                    'suggestions': [
                        '优化慢查询SQL',
                        '添加必要的索引',
                        '分析查询执行计划',
                        '考虑查询缓存'
                    ]
                })
            
            if mysql_analysis['buffer_pool']['bottleneck']:
                self.recommendations.append({
                    'category': 'MySQL缓冲池优化',
                    'priority': 'medium',
                    'issue': f"InnoDB缓冲池命中率过低: {mysql_analysis['buffer_pool']['hit_rate']}%",
                    'suggestions': [
                        '增加innodb_buffer_pool_size',
                        '优化数据访问模式',
                        '考虑数据分区',
                        '预热缓冲池'
                    ]
                })
        
        except Exception as e:
            print(f"MySQL性能分析失败: {e}")
    
    def analyze_redis_performance(self):
        """分析Redis性能"""
        print("🔍 分析Redis性能...")
        
        try:
            r = redis.Redis(host='localhost', port=6379, decode_responses=True)
            
            # 获取Redis信息
            info = r.info()
            
            redis_analysis = {
                'memory': {
                    'used_mb': round(info['used_memory'] / (1024**2), 2),
                    'peak_mb': round(info['used_memory_peak'] / (1024**2), 2),
                    'fragmentation_ratio': info.get('mem_fragmentation_ratio', 1.0),
                    'bottleneck': info.get('mem_fragmentation_ratio', 1.0) > 1.5
                },
                'connections': {
                    'connected_clients': info['connected_clients'],
                    'blocked_clients': info.get('blocked_clients', 0),
                    'bottleneck': info['connected_clients'] > 1000
                },
                'performance': {
                    'keyspace_hits': info.get('keyspace_hits', 0),
                    'keyspace_misses': info.get('keyspace_misses', 0),
                    'hit_rate': 0,
                    'ops_per_sec': info.get('instantaneous_ops_per_sec', 0),
                    'bottleneck': False
                }
            }
            
            # 计算命中率
            total_requests = redis_analysis['performance']['keyspace_hits'] + redis_analysis['performance']['keyspace_misses']
            if total_requests > 0:
                hit_rate = (redis_analysis['performance']['keyspace_hits'] / total_requests) * 100
                redis_analysis['performance']['hit_rate'] = round(hit_rate, 2)
                redis_analysis['performance']['bottleneck'] = hit_rate < 90
            
            self.analysis_results['redis_performance'] = redis_analysis
            
            # 生成Redis优化建议
            if redis_analysis['memory']['bottleneck']:
                self.recommendations.append({
                    'category': 'Redis内存优化',
                    'priority': 'medium',
                    'issue': f"Redis内存碎片率过高: {redis_analysis['memory']['fragmentation_ratio']:.2f}",
                    'suggestions': [
                        '启用内存碎片整理',
                        '优化数据结构使用',
                        '调整内存分配策略',
                        '考虑重启Redis服务'
                    ]
                })
            
            if redis_analysis['connections']['bottleneck']:
                self.recommendations.append({
                    'category': 'Redis连接优化',
                    'priority': 'high',
                    'issue': f"Redis连接数过高: {redis_analysis['connections']['connected_clients']}",
                    'suggestions': [
                        '优化连接池配置',
                        '增加maxclients参数',
                        '检查连接泄漏',
                        '实现连接复用'
                    ]
                })
            
            if redis_analysis['performance']['bottleneck']:
                self.recommendations.append({
                    'category': 'Redis缓存优化',
                    'priority': 'high',
                    'issue': f"Redis缓存命中率过低: {redis_analysis['performance']['hit_rate']}%",
                    'suggestions': [
                        '优化缓存策略',
                        '调整缓存过期时间',
                        '实现缓存预热',
                        '分析缓存访问模式'
                    ]
                })
        
        except Exception as e:
            print(f"Redis性能分析失败: {e}")
    
    def analyze_application_performance(self, jtl_file):
        """分析应用性能"""
        print("🔍 分析应用性能...")
        
        try:
            # 读取JTL测试结果
            with open(jtl_file, 'r', encoding='utf-8') as f:
                import csv
                reader = csv.DictReader(f)
                results = list(reader)
            
            if not results:
                print("未找到测试结果数据")
                return
            
            # 分析响应时间分布
            response_times = [int(r['elapsed']) for r in results]
            response_times.sort()
            
            # 分析错误分布
            errors = [r for r in results if r['success'] != 'true']
            error_codes = defaultdict(int)
            for error in errors:
                error_codes[error['responseCode']] += 1
            
            # 分析接口性能
            endpoint_performance = defaultdict(list)
            for result in results:
                endpoint_performance[result['label']].append(int(result['elapsed']))
            
            app_analysis = {
                'response_time': {
                    'avg': sum(response_times) / len(response_times),
                    'p50': response_times[int(len(response_times) * 0.5)],
                    'p95': response_times[int(len(response_times) * 0.95)],
                    'p99': response_times[int(len(response_times) * 0.99)],
                    'max': max(response_times),
                    'bottleneck': response_times[int(len(response_times) * 0.95)] > 500
                },
                'error_rate': {
                    'total_requests': len(results),
                    'error_requests': len(errors),
                    'error_rate_percent': (len(errors) / len(results)) * 100,
                    'error_codes': dict(error_codes),
                    'bottleneck': (len(errors) / len(results)) * 100 > 1
                },
                'endpoint_analysis': {}
            }
            
            # 分析各接口性能
            for endpoint, times in endpoint_performance.items():
                times.sort()
                app_analysis['endpoint_analysis'][endpoint] = {
                    'avg_response_time': sum(times) / len(times),
                    'p95_response_time': times[int(len(times) * 0.95)],
                    'request_count': len(times),
                    'bottleneck': times[int(len(times) * 0.95)] > 500
                }
            
            self.analysis_results['application_performance'] = app_analysis
            
            # 生成应用性能优化建议
            if app_analysis['response_time']['bottleneck']:
                self.recommendations.append({
                    'category': '应用响应时间优化',
                    'priority': 'high',
                    'issue': f"95%响应时间过长: {app_analysis['response_time']['p95']}ms",
                    'suggestions': [
                        '优化业务逻辑复杂度',
                        '实现异步处理',
                        '优化数据库查询',
                        '增加缓存层'
                    ]
                })
            
            if app_analysis['error_rate']['bottleneck']:
                self.recommendations.append({
                    'category': '应用错误率优化',
                    'priority': 'high',
                    'issue': f"错误率过高: {app_analysis['error_rate']['error_rate_percent']:.2f}%",
                    'suggestions': [
                        '分析错误日志',
                        '实现熔断机制',
                        '增加重试逻辑',
                        '优化异常处理'
                    ]
                })
        
        except Exception as e:
            print(f"应用性能分析失败: {e}")
    
    def generate_optimization_plan(self):
        """生成优化计划"""
        print("📋 生成系统优化计划...")
        
        # 按优先级排序建议
        high_priority = [r for r in self.recommendations if r['priority'] == 'high']
        medium_priority = [r for r in self.recommendations if r['priority'] == 'medium']
        low_priority = [r for r in self.recommendations if r['priority'] == 'low']
        
        optimization_plan = {
            'immediate_actions': high_priority,
            'short_term_actions': medium_priority,
            'long_term_actions': low_priority,
            'estimated_impact': self._estimate_optimization_impact()
        }
        
        return optimization_plan
    
    def _estimate_optimization_impact(self):
        """估算优化影响"""
        impact_estimation = {
            'performance_improvement': '20-40%',
            'response_time_reduction': '30-50%',
            'error_rate_reduction': '50-80%',
            'resource_utilization_improvement': '15-30%'
        }
        
        return impact_estimation
    
    def generate_report(self, jtl_file=None):
        """生成完整的瓶颈分析报告"""
        print("=" * 60)
        print("智能内容推荐平台系统瓶颈分析报告")
        print("=" * 60)
        print(f"分析时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print()
        
        # 执行各项分析
        self.analyze_system_resources()
        self.analyze_jvm_performance()
        self.analyze_database_performance()
        self.analyze_redis_performance()
        
        if jtl_file:
            self.analyze_application_performance(jtl_file)
        
        # 生成优化计划
        optimization_plan = self.generate_optimization_plan()
        
        # 输出分析结果
        print("🔍 系统资源分析")
        print("-" * 30)
        system_resources = self.analysis_results.get('system_resources', {})
        if system_resources:
            print(f"CPU使用率: {system_resources['cpu']['usage_percent']}%")
            print(f"内存使用率: {system_resources['memory']['usage_percent']}%")
            print(f"磁盘使用率: {system_resources['disk']['usage_percent']:.1f}%")
        print()
        
        print("🔍 数据库性能分析")
        print("-" * 30)
        mysql_perf = self.analysis_results.get('mysql_performance', {})
        if mysql_perf:
            print(f"MySQL连接数: {mysql_perf['connections']['connected']}")
            print(f"慢查询比例: {mysql_perf['queries']['slow_ratio']:.2f}%")
            print(f"缓冲池命中率: {mysql_perf['buffer_pool']['hit_rate']}%")
        print()
        
        print("🔍 缓存性能分析")
        print("-" * 30)
        redis_perf = self.analysis_results.get('redis_performance', {})
        if redis_perf:
            print(f"Redis内存使用: {redis_perf['memory']['used_mb']}MB")
            print(f"Redis连接数: {redis_perf['connections']['connected_clients']}")
            print(f"缓存命中率: {redis_perf['performance']['hit_rate']}%")
        print()
        
        # 输出优化建议
        print("💡 系统优化建议")
        print("-" * 30)
        
        if optimization_plan['immediate_actions']:
            print("🚨 紧急优化 (立即执行):")
            for i, action in enumerate(optimization_plan['immediate_actions'], 1):
                print(f"{i}. {action['category']}: {action['issue']}")
                for suggestion in action['suggestions'][:2]:  # 只显示前2个建议
                    print(f"   - {suggestion}")
                print()
        
        if optimization_plan['short_term_actions']:
            print("⚡ 短期优化 (1-2周内):")
            for i, action in enumerate(optimization_plan['short_term_actions'], 1):
                print(f"{i}. {action['category']}: {action['issue']}")
                for suggestion in action['suggestions'][:2]:
                    print(f"   - {suggestion}")
                print()
        
        # 保存详细报告
        report_data = {
            'timestamp': datetime.now().isoformat(),
            'analysis_results': self.analysis_results,
            'optimization_plan': optimization_plan,
            'recommendations': self.recommendations
        }
        
        report_file = 'bottleneck_analysis_report.json'
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, ensure_ascii=False, indent=2)
        
        print(f"📄 详细分析报告已保存至: {report_file}")
        print("=" * 60)

def main():
    if len(sys.argv) > 1:
        jtl_file = sys.argv[1]
    else:
        jtl_file = None
    
    analyzer = BottleneckAnalyzer()
    analyzer.generate_report(jtl_file)

if __name__ == '__main__':
    main()