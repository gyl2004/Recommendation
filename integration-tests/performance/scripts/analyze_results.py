#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
智能内容推荐平台性能测试结果分析脚本
"""

import sys
import csv
import json
import statistics
from datetime import datetime
from collections import defaultdict, Counter

class PerformanceAnalyzer:
    def __init__(self, jtl_file):
        self.jtl_file = jtl_file
        self.results = []
        self.load_results()
    
    def load_results(self):
        """加载JTL测试结果文件"""
        try:
            with open(self.jtl_file, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                for row in reader:
                    self.results.append({
                        'timestamp': int(row['timeStamp']),
                        'elapsed': int(row['elapsed']),
                        'label': row['label'],
                        'responseCode': row['responseCode'],
                        'success': row['success'] == 'true',
                        'bytes': int(row.get('bytes', 0)),
                        'sentBytes': int(row.get('sentBytes', 0)),
                        'latency': int(row.get('Latency', 0)),
                        'connect': int(row.get('Connect', 0))
                    })
            print(f"成功加载 {len(self.results)} 条测试结果")
        except Exception as e:
            print(f"加载结果文件失败: {e}")
            sys.exit(1)
    
    def calculate_basic_stats(self):
        """计算基础统计信息"""
        if not self.results:
            return {}
        
        # 响应时间统计
        response_times = [r['elapsed'] for r in self.results]
        success_count = sum(1 for r in self.results if r['success'])
        error_count = len(self.results) - success_count
        
        # 计算QPS
        if self.results:
            start_time = min(r['timestamp'] for r in self.results)
            end_time = max(r['timestamp'] for r in self.results)
            duration_seconds = (end_time - start_time) / 1000.0
            qps = len(self.results) / duration_seconds if duration_seconds > 0 else 0
        else:
            qps = 0
        
        stats = {
            'total_requests': len(self.results),
            'success_requests': success_count,
            'error_requests': error_count,
            'success_rate': (success_count / len(self.results)) * 100,
            'error_rate': (error_count / len(self.results)) * 100,
            'qps': qps,
            'avg_response_time': statistics.mean(response_times),
            'median_response_time': statistics.median(response_times),
            'min_response_time': min(response_times),
            'max_response_time': max(response_times),
            'p95_response_time': self.percentile(response_times, 95),
            'p99_response_time': self.percentile(response_times, 99),
            'std_response_time': statistics.stdev(response_times) if len(response_times) > 1 else 0
        }
        
        return stats
    
    def percentile(self, data, percentile):
        """计算百分位数"""
        if not data:
            return 0
        sorted_data = sorted(data)
        index = int((percentile / 100.0) * len(sorted_data))
        if index >= len(sorted_data):
            index = len(sorted_data) - 1
        return sorted_data[index]
    
    def analyze_by_endpoint(self):
        """按接口分析性能"""
        endpoint_stats = defaultdict(list)
        
        for result in self.results:
            endpoint_stats[result['label']].append(result)
        
        analysis = {}
        for endpoint, results in endpoint_stats.items():
            response_times = [r['elapsed'] for r in results]
            success_count = sum(1 for r in results if r['success'])
            
            analysis[endpoint] = {
                'total_requests': len(results),
                'success_requests': success_count,
                'success_rate': (success_count / len(results)) * 100,
                'avg_response_time': statistics.mean(response_times),
                'p95_response_time': self.percentile(response_times, 95),
                'p99_response_time': self.percentile(response_times, 99),
                'max_response_time': max(response_times),
                'min_response_time': min(response_times)
            }
        
        return analysis
    
    def analyze_error_patterns(self):
        """分析错误模式"""
        error_results = [r for r in self.results if not r['success']]
        
        if not error_results:
            return {'total_errors': 0, 'error_patterns': {}}
        
        # 按响应码分组
        error_codes = Counter(r['responseCode'] for r in error_results)
        
        # 按接口分组
        error_by_endpoint = defaultdict(list)
        for result in error_results:
            error_by_endpoint[result['label']].append(result['responseCode'])
        
        error_patterns = {}
        for endpoint, codes in error_by_endpoint.items():
            error_patterns[endpoint] = dict(Counter(codes))
        
        return {
            'total_errors': len(error_results),
            'error_codes': dict(error_codes),
            'error_by_endpoint': error_patterns
        }
    
    def check_performance_targets(self, stats):
        """检查性能目标达成情况"""
        targets = {
            'qps_target': 10000,
            'response_time_target': 500,  # ms
            'availability_target': 99.9   # %
        }
        
        results = {
            'qps_achieved': stats['qps'] >= targets['qps_target'],
            'response_time_achieved': stats['p95_response_time'] <= targets['response_time_target'],
            'availability_achieved': stats['success_rate'] >= targets['availability_target'],
            'targets': targets,
            'actual': {
                'qps': stats['qps'],
                'p95_response_time': stats['p95_response_time'],
                'availability': stats['success_rate']
            }
        }
        
        return results
    
    def generate_recommendations(self, stats, endpoint_analysis, target_check):
        """生成优化建议"""
        recommendations = []
        
        # QPS优化建议
        if not target_check['qps_achieved']:
            recommendations.append({
                'category': 'QPS优化',
                'issue': f"当前QPS {stats['qps']:.2f} 未达到目标 {target_check['targets']['qps_target']}",
                'suggestions': [
                    '增加应用实例数量，实现水平扩展',
                    '优化数据库连接池配置',
                    '启用Redis集群提高缓存性能',
                    '使用异步处理减少阻塞操作',
                    '优化JVM参数，增加堆内存'
                ]
            })
        
        # 响应时间优化建议
        if not target_check['response_time_achieved']:
            recommendations.append({
                'category': '响应时间优化',
                'issue': f"95%响应时间 {stats['p95_response_time']:.2f}ms 超过目标 {target_check['targets']['response_time_target']}ms",
                'suggestions': [
                    '实现多级缓存策略，提高缓存命中率',
                    '优化数据库查询，添加必要索引',
                    '使用CDN加速静态资源',
                    '实现数据库读写分离',
                    '优化算法复杂度，减少计算时间'
                ]
            })
        
        # 可用性优化建议
        if not target_check['availability_achieved']:
            recommendations.append({
                'category': '可用性优化',
                'issue': f"成功率 {stats['success_rate']:.2f}% 未达到目标 {target_check['targets']['availability_target']}%",
                'suggestions': [
                    '实现服务熔断和降级机制',
                    '增加健康检查和自动重启',
                    '实现负载均衡和故障转移',
                    '增加监控告警机制',
                    '优化错误处理和重试逻辑'
                ]
            })
        
        # 接口特定建议
        for endpoint, analysis in endpoint_analysis.items():
            if analysis['p95_response_time'] > 500:
                recommendations.append({
                    'category': f'{endpoint} 接口优化',
                    'issue': f"接口响应时间过长: {analysis['p95_response_time']:.2f}ms",
                    'suggestions': [
                        '检查该接口的业务逻辑复杂度',
                        '优化数据库查询和索引',
                        '实现接口级别的缓存',
                        '考虑异步处理非关键操作'
                    ]
                })
        
        return recommendations
    
    def generate_report(self):
        """生成完整的性能分析报告"""
        print("=" * 60)
        print("智能内容推荐平台性能测试分析报告")
        print("=" * 60)
        print(f"分析时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"测试数据文件: {self.jtl_file}")
        print()
        
        # 基础统计
        stats = self.calculate_basic_stats()
        print("📊 基础性能指标")
        print("-" * 30)
        print(f"总请求数: {stats['total_requests']:,}")
        print(f"成功请求数: {stats['success_requests']:,}")
        print(f"失败请求数: {stats['error_requests']:,}")
        print(f"成功率: {stats['success_rate']:.2f}%")
        print(f"QPS: {stats['qps']:.2f}")
        print(f"平均响应时间: {stats['avg_response_time']:.2f}ms")
        print(f"中位数响应时间: {stats['median_response_time']:.2f}ms")
        print(f"95%响应时间: {stats['p95_response_time']:.2f}ms")
        print(f"99%响应时间: {stats['p99_response_time']:.2f}ms")
        print(f"最大响应时间: {stats['max_response_time']:.2f}ms")
        print()
        
        # 接口分析
        endpoint_analysis = self.analyze_by_endpoint()
        print("🔍 接口性能分析")
        print("-" * 30)
        for endpoint, analysis in endpoint_analysis.items():
            print(f"接口: {endpoint}")
            print(f"  请求数: {analysis['total_requests']:,}")
            print(f"  成功率: {analysis['success_rate']:.2f}%")
            print(f"  平均响应时间: {analysis['avg_response_time']:.2f}ms")
            print(f"  95%响应时间: {analysis['p95_response_time']:.2f}ms")
            print()
        
        # 错误分析
        error_analysis = self.analyze_error_patterns()
        if error_analysis['total_errors'] > 0:
            print("❌ 错误分析")
            print("-" * 30)
            print(f"总错误数: {error_analysis['total_errors']:,}")
            print("错误码分布:")
            for code, count in error_analysis['error_codes'].items():
                print(f"  {code}: {count:,}")
            print()
        
        # 性能目标检查
        target_check = self.check_performance_targets(stats)
        print("🎯 性能目标达成情况")
        print("-" * 30)
        print(f"QPS目标 ({target_check['targets']['qps_target']}): {'✅ 达成' if target_check['qps_achieved'] else '❌ 未达成'} (实际: {target_check['actual']['qps']:.2f})")
        print(f"响应时间目标 ({target_check['targets']['response_time_target']}ms): {'✅ 达成' if target_check['response_time_achieved'] else '❌ 未达成'} (实际: {target_check['actual']['p95_response_time']:.2f}ms)")
        print(f"可用性目标 ({target_check['targets']['availability_target']}%): {'✅ 达成' if target_check['availability_achieved'] else '❌ 未达成'} (实际: {target_check['actual']['availability']:.2f}%)")
        print()
        
        # 优化建议
        recommendations = self.generate_recommendations(stats, endpoint_analysis, target_check)
        if recommendations:
            print("💡 优化建议")
            print("-" * 30)
            for i, rec in enumerate(recommendations, 1):
                print(f"{i}. {rec['category']}")
                print(f"   问题: {rec['issue']}")
                print("   建议:")
                for suggestion in rec['suggestions']:
                    print(f"   - {suggestion}")
                print()
        
        # 保存JSON报告
        report_data = {
            'timestamp': datetime.now().isoformat(),
            'basic_stats': stats,
            'endpoint_analysis': endpoint_analysis,
            'error_analysis': error_analysis,
            'target_check': target_check,
            'recommendations': recommendations
        }
        
        report_file = self.jtl_file.replace('.jtl', '_analysis.json')
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, ensure_ascii=False, indent=2)
        
        print(f"📄 详细报告已保存至: {report_file}")
        print("=" * 60)

def main():
    if len(sys.argv) != 2:
        print("使用方法: python3 analyze_results.py <jtl_file>")
        sys.exit(1)
    
    jtl_file = sys.argv[1]
    analyzer = PerformanceAnalyzer(jtl_file)
    analyzer.generate_report()

if __name__ == '__main__':
    main()