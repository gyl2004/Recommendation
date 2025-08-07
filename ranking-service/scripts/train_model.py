"""
Wide&Deep模型训练脚本
"""
import os
import sys
import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from loguru import logger
import argparse
from datetime import datetime

# 添加项目路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.wide_deep_model import WideDeepModel, create_wide_deep_feature_columns
from app.features.feature_pipeline import FeaturePipeline, create_sample_feature_config
from app.models.model_evaluator import ModelEvaluator


def generate_sample_data(num_samples: int = 10000) -> pd.DataFrame:
    """生成示例训练数据"""
    np.random.seed(42)
    
    data = {
        # 用户特征
        'user_id': [f'user_{i}' for i in range(num_samples)],
        'user_age': np.random.normal(30, 10, num_samples).clip(18, 80),
        'user_gender': np.random.choice(['M', 'F', 'Unknown'], num_samples, p=[0.45, 0.45, 0.1]),
        'user_activity_score': np.random.beta(2, 5, num_samples),
        'user_interests': np.random.choice(['tech', 'sports', 'travel', 'food', 'music'], num_samples),
        
        # 内容特征
        'content_id': [f'content_{i}' for i in range(num_samples)],
        'content_type': np.random.choice(['article', 'video', 'product'], num_samples, p=[0.5, 0.3, 0.2]),
        'content_category': np.random.choice([f'category_{i}' for i in range(20)], num_samples),
        'content_hot_score': np.random.beta(2, 3, num_samples),
        'content_duration': np.random.exponential(300, num_samples).clip(10, 3600),
        
        # 上下文特征
        'hour': np.random.randint(0, 24, num_samples),
        'day_of_week': np.random.randint(0, 7, num_samples),
        'device_type': np.random.choice(['mobile', 'tablet', 'desktop'], num_samples, p=[0.6, 0.2, 0.2]),
        
        # 交互特征
        'user_content_similarity': np.random.beta(1, 3, num_samples),
    }
    
    # 生成标签（点击率）
    # 基于特征的简单规则生成标签
    click_prob = (
        0.1 +  # 基础点击率
        0.3 * data['content_hot_score'] +  # 内容热度影响
        0.2 * data['user_activity_score'] +  # 用户活跃度影响
        0.2 * data['user_content_similarity'] +  # 相似度影响
        0.1 * (np.array(data['content_type']) == 'video').astype(float)  # 视频内容加成
    )
    
    # 添加噪声
    click_prob += np.random.normal(0, 0.1, num_samples)
    click_prob = np.clip(click_prob, 0, 1)
    
    data['label'] = np.random.binomial(1, click_prob, num_samples)
    
    return pd.DataFrame(data)


def create_tf_dataset(features_df: pd.DataFrame, labels: pd.Series, batch_size: int = 256, shuffle: bool = True) -> tf.data.Dataset:
    """创建TensorFlow数据集"""
    # 转换为字典格式
    feature_dict = {}
    for column in features_df.columns:
        if features_df[column].dtype == 'object':
            feature_dict[column] = features_df[column].astype(str).values
        else:
            feature_dict[column] = features_df[column].astype(np.float32).values
    
    # 创建数据集
    dataset = tf.data.Dataset.from_tensor_slices((feature_dict, labels.values.astype(np.float32)))
    
    if shuffle:
        dataset = dataset.shuffle(buffer_size=10000, seed=42)
    
    dataset = dataset.batch(batch_size)
    dataset = dataset.prefetch(tf.data.AUTOTUNE)
    
    return dataset


def train_wide_deep_model(data_path: str = None, 
                         model_save_path: str = "models/wide_deep_model",
                         pipeline_save_path: str = "models/feature_pipeline.pkl",
                         epochs: int = 10,
                         batch_size: int = 256):
    """训练Wide&Deep模型"""
    
    logger.info("开始训练Wide&Deep模型")
    
    # 加载或生成数据
    if data_path and os.path.exists(data_path):
        logger.info(f"从 {data_path} 加载数据")
        data = pd.read_csv(data_path)
    else:
        logger.info("生成示例数据")
        data = generate_sample_data(50000)
    
    logger.info(f"数据集大小: {len(data)}")
    logger.info(f"正样本比例: {data['label'].mean():.3f}")
    
    # 分离特征和标签
    feature_columns = [col for col in data.columns if col not in ['label', 'user_id', 'content_id']]
    features = data[feature_columns]
    labels = data['label']
    
    # 划分训练集和验证集
    X_train, X_val, y_train, y_val = train_test_split(
        features, labels, test_size=0.2, random_state=42, stratify=labels
    )
    
    logger.info(f"训练集大小: {len(X_train)}")
    logger.info(f"验证集大小: {len(X_val)}")
    
    # 创建特征管道
    logger.info("创建特征管道")
    feature_config = create_sample_feature_config()
    pipeline = FeaturePipeline()
    
    # 拟合特征管道
    X_train_processed = pipeline.fit_transform(X_train, feature_config)
    X_val_processed = pipeline.transform(X_val)
    
    logger.info(f"处理后特征维度: {X_train_processed.shape[1]}")
    
    # 保存特征管道
    os.makedirs(os.path.dirname(pipeline_save_path), exist_ok=True)
    pipeline.save_pipeline(pipeline_save_path)
    
    # 创建TensorFlow数据集
    train_dataset = create_tf_dataset(X_train_processed, y_train, batch_size)
    val_dataset = create_tf_dataset(X_val_processed, y_val, batch_size)
    
    # 创建特征列
    wide_columns, deep_columns = create_wide_deep_feature_columns()
    
    # 创建模型
    logger.info("创建Wide&Deep模型")
    model = WideDeepModel(
        wide_feature_columns=wide_columns,
        deep_feature_columns=deep_columns,
        deep_hidden_units=[128, 64, 32],
        dropout_rate=0.1,
        learning_rate=0.001
    )
    
    # 打印模型结构
    logger.info("模型结构:")
    logger.info(model.get_model_summary())
    
    # 设置回调函数
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor='val_loss',
            patience=3,
            restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=2,
            min_lr=1e-6
        ),
        tf.keras.callbacks.ModelCheckpoint(
            filepath=f"{model_save_path}_checkpoint",
            monitor='val_auc',
            save_best_only=True,
            mode='max'
        )
    ]
    
    # 训练模型
    logger.info("开始训练模型")
    history = model.train(
        train_dataset=train_dataset,
        validation_dataset=val_dataset,
        epochs=epochs,
        callbacks=callbacks
    )
    
    # 保存模型
    os.makedirs(os.path.dirname(model_save_path), exist_ok=True)
    model.save_model(model_save_path)
    
    # 模型评估
    logger.info("开始模型评估")
    evaluator = ModelEvaluator()
    evaluation_report = evaluator.evaluate_model_performance(
        model.model,
        val_dataset,
        list(X_val_processed.columns),
        f"wide_deep_training_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    )
    
    # 保存评估报告
    eval_report_path = f"{model_save_path}_evaluation.json"
    evaluator.save_evaluation_report(evaluation_report, eval_report_path)
    
    # 生成评估图表
    y_val_pred = model.predict({
        col: X_val_processed[col].values for col in X_val_processed.columns
    })
    plot_path = f"{model_save_path}_evaluation_plots.png"
    evaluator.generate_evaluation_plots(
        y_val.values, 
        y_val_pred.flatten(), 
        plot_path
    )
    
    # 打印训练结果
    final_train_loss = history.history['loss'][-1]
    final_val_loss = history.history['val_loss'][-1]
    final_train_auc = history.history['auc'][-1]
    final_val_auc = history.history['val_auc'][-1]
    
    logger.info("训练完成!")
    logger.info(f"最终训练损失: {final_train_loss:.4f}")
    logger.info(f"最终验证损失: {final_val_loss:.4f}")
    logger.info(f"最终训练AUC: {final_train_auc:.4f}")
    logger.info(f"最终验证AUC: {final_val_auc:.4f}")
    logger.info(f"验证集评估AUC: {evaluation_report['metrics']['auc']:.4f}")
    logger.info(f"验证集评估F1: {evaluation_report['metrics']['f1_score']:.4f}")
    
    return model, pipeline, history


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='训练Wide&Deep推荐模型')
    parser.add_argument('--data_path', type=str, help='训练数据路径')
    parser.add_argument('--model_path', type=str, default='models/wide_deep_model', help='模型保存路径')
    parser.add_argument('--pipeline_path', type=str, default='models/feature_pipeline.pkl', help='特征管道保存路径')
    parser.add_argument('--epochs', type=int, default=10, help='训练轮数')
    parser.add_argument('--batch_size', type=int, default=256, help='批次大小')
    
    args = parser.parse_args()
    
    # 配置日志
    logger.add("logs/training.log", rotation="1 day", retention="7 days")
    
    try:
        model, pipeline, history = train_wide_deep_model(
            data_path=args.data_path,
            model_save_path=args.model_path,
            pipeline_save_path=args.pipeline_path,
            epochs=args.epochs,
            batch_size=args.batch_size
        )
        
        logger.info("模型训练成功完成")
        
    except Exception as e:
        logger.error(f"模型训练失败: {e}")
        raise


if __name__ == "__main__":
    main()