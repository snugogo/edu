#!/bin/bash
# License 服务启动脚本

cd "$(dirname "$0")"

# 创建虚拟环境
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi

# 激活虚拟环境
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 启动服务
echo "========================================"
echo "License 管理服务启动中..."
echo "管理后台: http://localhost:5000"
echo "API 接口: http://localhost:5000/api/v1/..."
echo "========================================"

python app.py
