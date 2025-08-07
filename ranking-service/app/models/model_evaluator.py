"""
模型评估器
提供离线和在线模型评估功能
"""
import numpy as np
import pandas as pd
from typing import Dict, List, Tuple, Optional, Any
from sklearn.metrics import (
    roc_auc_score, precision_recall_curve, roc_curve,
    accuracy_score, precision_score, recall_score, f1_score,
    log_loss, mean_squared_error
)
import matplotlib.pyplot as plt
import seaborn as sns
from loguru import logger
import json
from datetime import datetime


class ModelEvaluator:
    """模型评估器"""
    
    def __init__(self):
        self.evaluation_history = []
    
    def evaluate_binary_classification(self, 
                                     y_true: np.ndarray, 
                                     y_pred: np.ndarray,
                                     y_pred_proba: Optional[np.ndarray] = None,
                                     threshold: float = 0.5) -> Dict[str, float]:
        """
        评估二分类模型
        
        Args:
            y_true: 真实标签
            y_pred: 预测标签
            y_pred_proba: 预测概率
            threshold: 分类阈值
            
        Returns:
            评估指标字典
        """
        metrics = {}
        
        # 基础分类指标
        metrics['accuracy'] = accuracy_score(y_true, y_pred)
        metrics['precision'] = precision_score(y_true, y_pred, average='binary')
        metrics['recall'] = recall_score(y_true, y_pred, average='binary')
        metrics['f1_score'] = f1_score(y_true, y_pred, average='binary')
        
        # 如果有预测概率，计算更多指标
        if y_pred_proba is not None:
            metrics['auc'] = roc_auc_score(y_true, y_pred_proba)
            metrics['log_loss'] = log_loss(y_true, y_pred_proba)
            
            # 计算不同阈值下的指标
            precision_curve, recall_curve, thresholds = precision_recall_curve(y_true, y_pred_proba)
            
            # PR AUC
            from sklearn.metrics import auc
            metrics['pr_auc'] = auc(recall_curve, precision_curve)
            
            # 最佳F1阈值
            f1_scores = 2 * (precision_curve * recall_curve) / (precision_curve + recall_curve + 1e-8)
            best_f1_idx = np.argmax(f1_scores[:-1])  # 排除最后一个点
            metrics['best_f1_threshold'] = thresholds[best_f1_idx]
            metrics['best_f1_score'] = f1_scores[best_f1_idx]
        
        return metrics
    
    def evaluate_ranking_metrics(self, 
                                y_true: np.ndarray, 
                                y_pred: np.ndarray,
                                k_values: List[int] = [1, 3, 5, 10]) -> Dict[str, float]:
        """
        评估排序指标
        
        Args:
            y_true: 真实相关性分数
            y_pred: 预测相关性分数
            k_values: 评估的K值列表
            
        Returns:
            排序评估指标
        """
        metrics = {}
        
        # 计算NDCG
        for k in k_values:
            ndcg_k = self._calculate_ndcg(y_true, y_pred, k)
            metrics[f'ndcg@{k}'] = ndcg_k
        
        # 计算MAP
        map_score = self._calculate_map(y_true, y_pred)
        metrics['map'] = map_score
        
        # 计算MRR
        mrr_score = self._calculate_mrr(y_true, y_pred)
        metrics['mrr'] = mrr_score
        
        return metrics
    
    def _calculate_ndcg(self, y_true: np.ndarray, y_pred: np.ndarray, k: int) -> float:
        """计算NDCG@K"""
        # 按预测分数排序
        sorted_indices = np.argsort(y_pred)[::-1][:k]
        
        # 计算DCG
        dcg = 0.0
        for i, idx in enumerate(sorted_indices):
            rel = y_true[idx]
            dcg += (2**rel - 1) / np.log2(i + 2)
        
        # 计算IDCG
        ideal_sorted_indices = np.argsort(y_true)[::-1][:k]
        idcg = 0.0
        for i, idx in enumerate(ideal_sorted_indices):
            rel = y_true[idx]
            idcg += (2**rel - 1) / np.log2(i + 2)
        
        # 计算NDCG
        if idcg == 0:
            return 0.0
        return dcg / idcg
    
    def _calculate_map(self, y_true: np.ndarray, y_pred: np.ndarray) -> float:
        """计算MAP (Mean Average Precision)"""
        sorted_indices = np.argsort(y_pred)[::-1]
        
        relevant_count = 0
        precision_sum = 0.0
        total_relevant = np.sum(y_true > 0)
        
        if total_relevant == 0:
            return 0.0
        
        for i, idx in enumerate(sorted_indices):
            if y_true[idx] > 0:
                relevant_count += 1
                precision_at_i = relevant_count / (i + 1)
                precision_sum += precision_at_i
        
        return precision_sum / total_relevant
    
    def _calculate_mrr(self, y_true: np.ndarray, y_pred: np.ndarray) -> float:
        """计算MRR (Mean Reciprocal Rank)"""
        sorted_indices = np.argsort(y_pred)[::-1]
        
        for i, idx in enumerate(sorted_indices):
            if y_true[idx] > 0:
                return 1.0 / (i + 1)
        
        return 0.0
    
    def evaluate_model_performance(self, 
                                 model,
                                 test_dataset,
                                 feature_names: List[str],
                                 evaluation_name: str = "model_evaluation") -> Dict[str, Any]:
        """
        全面评估模型性能
        
        Args:
            model: 训练好的模型
            test_dataset: 测试数据集
            feature_names: 特征名称列表
            evaluation_name: 评估名称
            
        Returns:
            完整的评估结果
        """
        logger.info(f"开始模型评估: {evaluation_name}")
        
        # 收集预测结果
        y_true_list = []
        y_pred_list = []
        
        for batch_features, batch_labels in test_dataset:
            predictions = model.predict(batch_features)
            y_true_list.extend(batch_labels.numpy())
            y_pred_list.extend(predictions.flatten())
        
        y_true = np.array(y_true_list)
        y_pred_proba = np.array(y_pred_list)
        y_pred = (y_pred_proba > 0.5).astype(int)
        
        # 分类指标评估
        classification_metrics = self.evaluate_binary_classification(
            y_true, y_pred, y_pred_proba
        )
        
        # 排序指标评估
        ranking_metrics = self.evaluate_ranking_metrics(y_true, y_pred_proba)
        
        # 合并所有指标
        all_metrics = {
            **classification_metrics,
            **ranking_metrics
        }
        
        # 创建评估报告
        evaluation_report = {
            'evaluation_name': evaluation_name,
            'timestamp': datetime.now().isoformat(),
            'metrics': all_metrics,
            'data_stats': {
                'total_samples': len(y_true),
                'positive_samples': int(np.sum(y_true)),
                'negative_samples': int(len(y_true) - np.sum(y_true)),
                'positive_rate': float(np.mean(y_true))
            }
        }
        
        # 保存评估历史
        self.evaluation_history.append(evaluation_report)
        
        logger.info(f"模型评估完成: AUC={all_metrics['auc']:.4f}, F1={all_metrics['f1_score']:.4f}")
        
        return evaluation_report
    
    def compare_models(self, 
                      evaluation_reports: List[Dict[str, Any]],
                      primary_metric: str = 'auc') -> Dict[str, Any]:
        """
        比较多个模型的性能
        
        Args:
            evaluation_reports: 评估报告列表
            primary_metric: 主要比较指标
            
        Returns:
            模型比较结果
        """
        if not evaluation_reports:
            return {}
        
        comparison_data = []
        for report in evaluation_reports:
            model_data = {
                'model_name': report['evaluation_name'],
                'timestamp': report['timestamp'],
                **report['metrics']
            }
            comparison_data.append(model_data)
        
        # 转换为DataFrame便于分析
        df = pd.DataFrame(comparison_data)
        
        # 找出最佳模型
        best_model_idx = df[primary_metric].idxmax()
        best_model = df.iloc[best_model_idx]
        
        # 计算指标统计
        metric_stats = {}
        for metric in df.select_dtypes(include=[np.number]).columns:
            if metric != 'timestamp':
                metric_stats[metric] = {
                    'mean': float(df[metric].mean()),
                    'std': float(df[metric].std()),
                    'min': float(df[metric].min()),
                    'max': float(df[metric].max()),
                    'best_model': best_model['model_name']
                }
        
        comparison_result = {
            'comparison_timestamp': datetime.now().isoformat(),
            'primary_metric': primary_metric,
            'best_model': {
                'name': best_model['model_name'],
                'score': float(best_model[primary_metric])
            },
            'metric_statistics': metric_stats,
            'model_details': comparison_data
        }
        
        return comparison_result
    
    def generate_evaluation_plots(self, 
                                y_true: np.ndarray, 
                                y_pred_proba: np.ndarray,
                                save_path: Optional[str] = None) -> Dict[str, str]:
        """
        生成评估图表
        
        Args:
            y_true: 真实标签
            y_pred_proba: 预测概率
            save_path: 保存路径
            
        Returns:
            生成的图表路径字典
        """
        plot_paths = {}
        
        # 设置图表样式
        plt.style.use('seaborn-v0_8')
        fig, axes = plt.subplots(2, 2, figsize=(15, 12))
        
        # ROC曲线
        fpr, tpr, _ = roc_curve(y_true, y_pred_proba)
        auc_score = roc_auc_score(y_true, y_pred_proba)
        
        axes[0, 0].plot(fpr, tpr, label=f'ROC Curve (AUC = {auc_score:.3f})')
        axes[0, 0].plot([0, 1], [0, 1], 'k--', label='Random')
        axes[0, 0].set_xlabel('False Positive Rate')
        axes[0, 0].set_ylabel('True Positive Rate')
        axes[0, 0].set_title('ROC Curve')
        axes[0, 0].legend()
        axes[0, 0].grid(True)
        
        # PR曲线
        precision, recall, _ = precision_recall_curve(y_true, y_pred_proba)
        from sklearn.metrics import auc
        pr_auc = auc(recall, precision)
        
        axes[0, 1].plot(recall, precision, label=f'PR Curve (AUC = {pr_auc:.3f})')
        axes[0, 1].set_xlabel('Recall')
        axes[0, 1].set_ylabel('Precision')
        axes[0, 1].set_title('Precision-Recall Curve')
        axes[0, 1].legend()
        axes[0, 1].grid(True)
        
        # 预测分布
        axes[1, 0].hist(y_pred_proba[y_true == 0], bins=50, alpha=0.7, label='Negative', density=True)
        axes[1, 0].hist(y_pred_proba[y_true == 1], bins=50, alpha=0.7, label='Positive', density=True)
        axes[1, 0].set_xlabel('Predicted Probability')
        axes[1, 0].set_ylabel('Density')
        axes[1, 0].set_title('Prediction Distribution')
        axes[1, 0].legend()
        axes[1, 0].grid(True)
        
        # 校准曲线
        from sklearn.calibration import calibration_curve
        fraction_of_positives, mean_predicted_value = calibration_curve(
            y_true, y_pred_proba, n_bins=10
        )
        
        axes[1, 1].plot(mean_predicted_value, fraction_of_positives, "s-", label="Model")
        axes[1, 1].plot([0, 1], [0, 1], "k:", label="Perfectly calibrated")
        axes[1, 1].set_xlabel('Mean Predicted Probability')
        axes[1, 1].set_ylabel('Fraction of Positives')
        axes[1, 1].set_title('Calibration Curve')
        axes[1, 1].legend()
        axes[1, 1].grid(True)
        
        plt.tight_layout()
        
        if save_path:
            plt.savefig(save_path, dpi=300, bbox_inches='tight')
            plot_paths['evaluation_plots'] = save_path
            logger.info(f"评估图表已保存到: {save_path}")
        
        plt.close()
        
        return plot_paths
    
    def save_evaluation_report(self, 
                             evaluation_report: Dict[str, Any],
                             file_path: str):
        """保存评估报告"""
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(evaluation_report, f, ensure_ascii=False, indent=2)
        
        logger.info(f"评估报告已保存到: {file_path}")
    
    def load_evaluation_report(self, file_path: str) -> Dict[str, Any]:
        """加载评估报告"""
        with open(file_path, 'r', encoding='utf-8') as f:
            evaluation_report = json.load(f)
        
        logger.info(f"评估报告已从 {file_path} 加载")
        return evaluation_report
    
    def get_evaluation_summary(self) -> Dict[str, Any]:
        """获取评估历史摘要"""
        if not self.evaluation_history:
            return {'message': '暂无评估历史'}
        
        summary = {
            'total_evaluations': len(self.evaluation_history),
            'latest_evaluation': self.evaluation_history[-1]['evaluation_name'],
            'latest_timestamp': self.evaluation_history[-1]['timestamp'],
            'best_auc': max(
                report['metrics']['auc'] 
                for report in self.evaluation_history 
                if 'auc' in report['metrics']
            ),
            'evaluation_names': [
                report['evaluation_name'] 
                for report in self.evaluation_history
            ]
        }
        
        return summary


class OnlineEvaluator:
    """在线评估器"""
    
    def __init__(self, window_size: int = 1000):
        self.window_size = window_size
        self.predictions = []
        self.labels = []
        self.timestamps = []
    
    def add_prediction(self, prediction: float, label: int, timestamp: Optional[str] = None):
        """添加预测结果"""
        self.predictions.append(prediction)
        self.labels.append(label)
        self.timestamps.append(timestamp or datetime.now().isoformat())
        
        # 保持窗口大小
        if len(self.predictions) > self.window_size:
            self.predictions.pop(0)
            self.labels.pop(0)
            self.timestamps.pop(0)
    
    def get_current_metrics(self) -> Dict[str, float]:
        """获取当前窗口的指标"""
        if len(self.predictions) < 10:  # 最少需要10个样本
            return {'message': '样本数量不足'}
        
        y_true = np.array(self.labels)
        y_pred_proba = np.array(self.predictions)
        y_pred = (y_pred_proba > 0.5).astype(int)
        
        evaluator = ModelEvaluator()
        metrics = evaluator.evaluate_binary_classification(y_true, y_pred, y_pred_proba)
        
        # 添加在线特有的指标
        metrics['sample_count'] = len(self.predictions)
        metrics['positive_rate'] = float(np.mean(y_true))
        metrics['avg_prediction'] = float(np.mean(y_pred_proba))
        
        return metrics
    
    def detect_drift(self, baseline_metrics: Dict[str, float], threshold: float = 0.05) -> Dict[str, Any]:
        """检测模型漂移"""
        current_metrics = self.get_current_metrics()
        
        if 'message' in current_metrics:
            return current_metrics
        
        drift_detected = False
        drift_details = {}
        
        for metric_name in ['auc', 'accuracy', 'precision', 'recall']:
            if metric_name in baseline_metrics and metric_name in current_metrics:
                baseline_value = baseline_metrics[metric_name]
                current_value = current_metrics[metric_name]
                drift = abs(current_value - baseline_value)
                
                drift_details[metric_name] = {
                    'baseline': baseline_value,
                    'current': current_value,
                    'drift': drift,
                    'drift_detected': drift > threshold
                }
                
                if drift > threshold:
                    drift_detected = True
        
        return {
            'drift_detected': drift_detected,
            'threshold': threshold,
            'drift_details': drift_details,
            'timestamp': datetime.now().isoformat()
        }