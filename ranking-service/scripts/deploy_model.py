"""
模型部署脚本
用于部署训练好的Wide&Deep模型到生产环境
"""
import os
import sys
import argparse
import json
import shutil
from pathlib import Path
from loguru import logger
from datetime import datetime

# 添加项目路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.model_server import ModelManager, ModelServer
from app.models.model_evaluator import ModelEvaluator


class ModelDeployer:
    """模型部署器"""
    
    def __init__(self, deployment_config_path: str):
        """
        初始化部署器
        
        Args:
            deployment_config_path: 部署配置文件路径
        """
        self.config_path = deployment_config_path
        self.config = self._load_config()
        self.model_manager = ModelManager(self.config['models_dir'])
    
    def _load_config(self) -> dict:
        """加载部署配置"""
        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                config = json.load(f)
            
            # 设置默认值
            config.setdefault('models_dir', 'models')
            config.setdefault('backup_dir', 'models/backup')
            config.setdefault('evaluation_threshold', {
                'auc': 0.7,
                'f1_score': 0.6
            })
            config.setdefault('server_config', {
                'max_batch_size': 64,
                'batch_timeout_ms': 10,
                'max_workers': 4
            })
            
            return config
            
        except Exception as e:
            logger.error(f"加载部署配置失败: {e}")
            raise
    
    def validate_model(self, model_path: str, evaluation_path: str) -> bool:
        """
        验证模型是否满足部署要求
        
        Args:
            model_path: 模型路径
            evaluation_path: 评估报告路径
            
        Returns:
            是否通过验证
        """
        logger.info("开始模型验证")
        
        try:
            # 检查模型文件是否存在
            if not Path(model_path).exists():
                logger.error(f"模型文件不存在: {model_path}")
                return False
            
            # 检查评估报告
            if not Path(evaluation_path).exists():
                logger.error(f"评估报告不存在: {evaluation_path}")
                return False
            
            # 加载评估报告
            evaluator = ModelEvaluator()
            evaluation_report = evaluator.load_evaluation_report(evaluation_path)
            
            # 检查评估指标
            metrics = evaluation_report['metrics']
            thresholds = self.config['evaluation_threshold']
            
            for metric_name, threshold in thresholds.items():
                if metric_name in metrics:
                    metric_value = metrics[metric_name]
                    if metric_value < threshold:
                        logger.error(
                            f"模型指标不满足要求: {metric_name}={metric_value:.4f} < {threshold}"
                        )
                        return False
                    else:
                        logger.info(
                            f"模型指标验证通过: {metric_name}={metric_value:.4f} >= {threshold}"
                        )
            
            logger.info("模型验证通过")
            return True
            
        except Exception as e:
            logger.error(f"模型验证失败: {e}")
            return False
    
    def backup_current_model(self, model_name: str) -> bool:
        """
        备份当前模型
        
        Args:
            model_name: 模型名称
            
        Returns:
            是否备份成功
        """
        try:
            # 检查是否有当前模型
            current_model_path = Path(self.config['models_dir']) / model_name
            if not current_model_path.exists():
                logger.info("没有当前模型需要备份")
                return True
            
            # 创建备份目录
            backup_dir = Path(self.config['backup_dir'])
            backup_dir.mkdir(parents=True, exist_ok=True)
            
            # 生成备份路径
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            backup_path = backup_dir / f"{model_name}_backup_{timestamp}"
            
            # 执行备份
            shutil.copytree(current_model_path, backup_path)
            
            logger.info(f"模型备份成功: {backup_path}")
            return True
            
        except Exception as e:
            logger.error(f"模型备份失败: {e}")
            return False
    
    def deploy_model(self, 
                    model_name: str,
                    model_path: str,
                    evaluation_path: str,
                    force: bool = False) -> bool:
        """
        部署模型
        
        Args:
            model_name: 模型名称
            model_path: 模型路径
            evaluation_path: 评估报告路径
            force: 是否强制部署（跳过验证）
            
        Returns:
            是否部署成功
        """
        logger.info(f"开始部署模型: {model_name}")
        
        try:
            # 验证模型（除非强制部署）
            if not force:
                if not self.validate_model(model_path, evaluation_path):
                    logger.error("模型验证失败，部署终止")
                    return False
            
            # 备份当前模型
            if not self.backup_current_model(model_name):
                logger.error("模型备份失败，部署终止")
                return False
            
            # 停止当前模型服务器（如果正在运行）
            if model_name in self.model_manager.active_servers:
                logger.info("停止当前模型服务器")
                self.model_manager.stop_model_server(model_name)
            
            # 复制新模型到部署目录
            deploy_path = Path(self.config['models_dir']) / model_name
            if deploy_path.exists():
                shutil.rmtree(deploy_path)
            
            shutil.copytree(model_path, deploy_path)
            
            # 复制评估报告
            eval_deploy_path = deploy_path.parent / f"{model_name}_evaluation.json"
            shutil.copy2(evaluation_path, eval_deploy_path)
            
            # 注册模型
            self.model_manager.register_model(
                model_name=model_name,
                model_path=str(deploy_path),
                config=self.config['server_config']
            )
            
            # 启动模型服务器
            if self.model_manager.start_model_server(model_name):
                logger.info(f"模型部署成功: {model_name}")
                
                # 执行部署后验证
                if self._post_deployment_validation(model_name):
                    logger.info("部署后验证通过")
                    return True
                else:
                    logger.error("部署后验证失败")
                    return False
            else:
                logger.error("模型服务器启动失败")
                return False
                
        except Exception as e:
            logger.error(f"模型部署失败: {e}")
            return False
    
    def _post_deployment_validation(self, model_name: str) -> bool:
        """部署后验证"""
        try:
            # 获取模型服务器
            server = self.model_manager.get_model_server(model_name)
            if not server:
                return False
            
            # 健康检查
            health = server.health_check()
            if health['status'] != 'healthy':
                logger.error(f"模型服务器健康检查失败: {health}")
                return False
            
            # 测试预测
            test_features = {
                'user_age': 25.0,
                'user_gender': 'M',
                'user_activity_score': 0.5,
                'content_hot_score': 0.7,
                'content_type': 'article',
                'content_category': 'tech',
                'user_interests': 'tech'
            }
            
            scores = server.predict_batch_sync([test_features])
            if len(scores) != 1 or not (0 <= scores[0] <= 1):
                logger.error(f"测试预测失败: {scores}")
                return False
            
            logger.info(f"测试预测成功: {scores[0]:.4f}")
            return True
            
        except Exception as e:
            logger.error(f"部署后验证失败: {e}")
            return False
    
    def rollback_model(self, model_name: str, backup_timestamp: str = None) -> bool:
        """
        回滚模型
        
        Args:
            model_name: 模型名称
            backup_timestamp: 备份时间戳，如果为None则使用最新备份
            
        Returns:
            是否回滚成功
        """
        logger.info(f"开始回滚模型: {model_name}")
        
        try:
            backup_dir = Path(self.config['backup_dir'])
            
            # 查找备份文件
            if backup_timestamp:
                backup_path = backup_dir / f"{model_name}_backup_{backup_timestamp}"
            else:
                # 找到最新的备份
                backup_files = list(backup_dir.glob(f"{model_name}_backup_*"))
                if not backup_files:
                    logger.error("没有找到备份文件")
                    return False
                
                backup_path = max(backup_files, key=lambda p: p.stat().st_mtime)
            
            if not backup_path.exists():
                logger.error(f"备份文件不存在: {backup_path}")
                return False
            
            # 停止当前模型服务器
            if model_name in self.model_manager.active_servers:
                self.model_manager.stop_model_server(model_name)
            
            # 恢复备份
            current_path = Path(self.config['models_dir']) / model_name
            if current_path.exists():
                shutil.rmtree(current_path)
            
            shutil.copytree(backup_path, current_path)
            
            # 重新启动模型服务器
            self.model_manager.register_model(
                model_name=model_name,
                model_path=str(current_path),
                config=self.config['server_config']
            )
            
            if self.model_manager.start_model_server(model_name):
                logger.info(f"模型回滚成功: {model_name}")
                return True
            else:
                logger.error("模型服务器启动失败")
                return False
                
        except Exception as e:
            logger.error(f"模型回滚失败: {e}")
            return False
    
    def list_deployments(self) -> dict:
        """列出所有部署"""
        return self.model_manager.list_models()
    
    def get_deployment_status(self, model_name: str) -> dict:
        """获取部署状态"""
        models_info = self.model_manager.list_models()
        return models_info.get(model_name, {'error': '模型不存在'})


def create_default_config(config_path: str):
    """创建默认配置文件"""
    default_config = {
        "models_dir": "models",
        "backup_dir": "models/backup",
        "evaluation_threshold": {
            "auc": 0.7,
            "f1_score": 0.6,
            "precision": 0.6,
            "recall": 0.6
        },
        "server_config": {
            "max_batch_size": 64,
            "batch_timeout_ms": 10,
            "max_workers": 4
        }
    }
    
    with open(config_path, 'w', encoding='utf-8') as f:
        json.dump(default_config, f, ensure_ascii=False, indent=2)
    
    logger.info(f"默认配置文件已创建: {config_path}")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='模型部署工具')
    parser.add_argument('--config', type=str, default='config/deployment.json', help='部署配置文件路径')
    
    subparsers = parser.add_subparsers(dest='command', help='可用命令')
    
    # 部署命令
    deploy_parser = subparsers.add_parser('deploy', help='部署模型')
    deploy_parser.add_argument('--name', type=str, required=True, help='模型名称')
    deploy_parser.add_argument('--model_path', type=str, required=True, help='模型路径')
    deploy_parser.add_argument('--evaluation_path', type=str, required=True, help='评估报告路径')
    deploy_parser.add_argument('--force', action='store_true', help='强制部署（跳过验证）')
    
    # 回滚命令
    rollback_parser = subparsers.add_parser('rollback', help='回滚模型')
    rollback_parser.add_argument('--name', type=str, required=True, help='模型名称')
    rollback_parser.add_argument('--timestamp', type=str, help='备份时间戳')
    
    # 列出部署
    subparsers.add_parser('list', help='列出所有部署')
    
    # 获取状态
    status_parser = subparsers.add_parser('status', help='获取部署状态')
    status_parser.add_argument('--name', type=str, required=True, help='模型名称')
    
    # 创建默认配置
    config_parser = subparsers.add_parser('init-config', help='创建默认配置文件')
    config_parser.add_argument('--path', type=str, default='config/deployment.json', help='配置文件路径')
    
    args = parser.parse_args()
    
    # 配置日志
    logger.add("logs/deployment.log", rotation="1 day", retention="7 days")
    
    try:
        if args.command == 'init-config':
            os.makedirs(os.path.dirname(args.path), exist_ok=True)
            create_default_config(args.path)
            return
        
        if args.command is None:
            parser.print_help()
            return
        
        # 创建部署器
        deployer = ModelDeployer(args.config)
        
        if args.command == 'deploy':
            success = deployer.deploy_model(
                model_name=args.name,
                model_path=args.model_path,
                evaluation_path=args.evaluation_path,
                force=args.force
            )
            if success:
                logger.info("部署成功")
            else:
                logger.error("部署失败")
                sys.exit(1)
        
        elif args.command == 'rollback':
            success = deployer.rollback_model(
                model_name=args.name,
                backup_timestamp=args.timestamp
            )
            if success:
                logger.info("回滚成功")
            else:
                logger.error("回滚失败")
                sys.exit(1)
        
        elif args.command == 'list':
            deployments = deployer.list_deployments()
            print(json.dumps(deployments, ensure_ascii=False, indent=2))
        
        elif args.command == 'status':
            status = deployer.get_deployment_status(args.name)
            print(json.dumps(status, ensure_ascii=False, indent=2))
        
    except Exception as e:
        logger.error(f"执行命令失败: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()