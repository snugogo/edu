# License 管理后端服务 - 完整版
import os
import sqlite3
import json
import uuid
import datetime
import hashlib
import hmac
import base64
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

DB_PATH = os.path.join(os.path.dirname(__file__), 'licenses.db')
UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), 'uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# 阿里云配置
ALIYUN_API_BASE = "https://eagent.edu-aliyun.com/gw/h/open/api/v1"
ALIYUN_APP_KEY = "YOUR_ALIYUN_APP_KEY"
ALIYUN_APP_SECRET = "YOUR_ALIYUN_APP_SECRET"

# OSS配置
OSS_CONFIG = {
    'access_key': 'YOUR_ACCESS_KEY',
    'access_secret': 'YOUR_ACCESS_SECRET',
    'bucket': 'your-bucket',
    'endpoint': 'oss-cn-hangzhou.aliyuncs.com'
}

def init_db():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    # 用户表
    c.execute('''CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        email TEXT,
        role TEXT DEFAULT 'user',
        parent_id INTEGER,
        language TEXT DEFAULT 'zh',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    # 主题表
    c.execute('''CREATE TABLE IF NOT EXISTS themes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        theme_name TEXT NOT NULL,
        theme_name_en TEXT,
        agent_code TEXT,
        agent_name TEXT,
        agent_name_en TEXT,
        images TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    # 课件资料表
    c.execute('''CREATE TABLE IF NOT EXISTS course_materials (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        title TEXT NOT NULL,
        title_en TEXT,
        file_type TEXT,
        file_url TEXT,
        file_size INTEGER,
        description TEXT,
        description_en TEXT,
        tags TEXT,
        view_count INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    # 设备表
    c.execute('''CREATE TABLE IF NOT EXISTS devices (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT UNIQUE,
        device_name TEXT,
        mac_address TEXT UNIQUE,
        sn TEXT,
        product_key TEXT,
        device_secret TEXT,
        status TEXT DEFAULT 'inactive',
        custom_name TEXT,
        theme_id INTEGER,
        language TEXT DEFAULT 'zh',
        last_active TIMESTAMP,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    # 紧急通知表
    c.execute('''CREATE TABLE IF NOT EXISTS notifications (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT, title_en TEXT,
        content TEXT, content_en TEXT,
        priority TEXT DEFAULT 'normal',
        created_by INTEGER,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        start_time TIMESTAMP, end_time TIMESTAMP
    )''')
    
    # 对话记录表
    c.execute('''CREATE TABLE IF NOT EXISTS conversations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT,
        user_query TEXT,
        ai_response TEXT,
        asr_text TEXT,
        tts_text TEXT,
        intent TEXT,
        sentiment TEXT,
        keywords TEXT,
        category TEXT,
        analysis_result TEXT,
        language TEXT DEFAULT 'zh',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    # OTA版本表
    c.execute('''CREATE TABLE IF NOT EXISTS ota_versions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        version_code INTEGER,
        version_name TEXT,
        description TEXT, description_en TEXT,
        download_url TEXT,
        force_update INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )''')
    
    conn.commit()
    
    # 默认管理员
    c.execute("SELECT * FROM users WHERE username = 'admin'")
    if not c.fetchone():
        c.execute("INSERT INTO users (username, password, role, language) VALUES (?, ?, ?, ?)", 
                  ('admin', 'admin123', 'admin', 'zh'))
    
    conn.commit()
    conn.close()

init_db()

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

# ==================== 用户认证 ====================

@app.route('/api/v1/auth/login', methods=['POST'])
def login():
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    c.execute('SELECT * FROM users WHERE username = ? AND password = ?', 
              (data.get('username'), data.get('password')))
    user = c.fetchone()
    conn.close()
    
    if user:
        return jsonify({'code': 200, 'data': {
            'userId': user['id'], 'username': user['username'],
            'email': user['email'], 'role': user['role'],
            'language': user['language'], 'token': str(uuid.uuid4())
        }})
    return jsonify({'code': 401, 'message': 'Invalid credentials'}), 401

@app.route('/api/v1/auth/register', methods=['POST'])
def register():
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    
    c.execute('SELECT * FROM users WHERE username = ?', (data.get('username'),))
    if c.fetchone():
        conn.close()
        return jsonify({'code': 400, 'message': 'Username exists'}), 400
    
    c.execute('''INSERT INTO users (username, password, email, role, parent_id, language) 
                 VALUES (?, ?, ?, 'user', ?, ?)''',
              (data.get('username'), data.get('password'), data.get('email'),
               data.get('parentId'), data.get('language', 'zh')))
    user_id = c.lastrowid
    c.execute('''INSERT INTO themes (user_id, theme_name, theme_name_en, agent_code, images)
                 VALUES (?, ?, ?, ?, '[]')''',
              (user_id, 'Default Theme', 'Default Theme', ''))
    conn.commit()
    conn.close()
    
    return jsonify({'code': 200, 'data': {'userId': user_id}})

# ==================== 课件资料管理 ====================

@app.route('/api/v1/materials', methods=['GET'])
def list_materials():
    """获取课件列表"""
    user_id = request.args.get('userId')
    conn = get_db()
    c = conn.cursor()
    c.execute('SELECT * FROM course_materials WHERE user_id = ? ORDER BY created_at DESC', (user_id,))
    materials = [dict(row) for row in c.fetchall()]
    conn.close()
    return jsonify({'code': 200, 'data': materials})

@app.route('/api/v1/materials', methods=['POST'])
def upload_material():
    """上传课件"""
    if 'file' not in request.files:
        return jsonify({'code': 400, 'message': 'No file'}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({'code': 400, 'message': 'No file selected'}), 400
    
    # 获取文件类型
    ext = file.filename.split('.')[-1].lower()
    file_type = 'other'
    if ext in ['pdf']: file_type = 'pdf'
    elif ext in ['doc', 'docx']: file_type = 'word'
    elif ext in ['mp4', 'avi', 'mov', 'mkv']: file_type = 'video'
    elif ext in ['ppt', 'pptx']: file_type = 'ppt'
    elif ext in ['jpg', 'jpeg', 'png', 'gif']: file_type = 'image'
    
    # 保存文件
    filename = f"{uuid.uuid4().hex}.{ext}"
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    file.save(filepath)
    
    # 获取文件大小
    file_size = os.path.getsize(filepath)
    
    # 生成访问URL
    file_url = f"/uploads/{filename}"
    
    # 保存到数据库
    data = request.form
    conn = get_db()
    c = conn.cursor()
    c.execute('''INSERT INTO course_materials 
                 (user_id, title, title_en, file_type, file_url, file_size, description, description_en, tags)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)''',
              (data.get('userId'), data.get('title'), data.get('titleEn'), 
               file_type, file_url, file_size,
               data.get('description', ''), data.get('descriptionEn', ''),
               data.get('tags', '')))
    material_id = c.lastrowid
    conn.commit()
    conn.close()
    
    return jsonify({'code': 200, 'data': {'id': material_id, 'url': file_url}})

@app.route('/api/v1/materials/<int:material_id>', methods=['DELETE'])
def delete_material(material_id):
    conn = get_db()
    c = conn.cursor()
    c.execute('SELECT file_url FROM course_materials WHERE id = ?', (material_id,))
    material = c.fetchone()
    if material:
        # 删除物理文件
        filepath = os.path.join(UPLOAD_FOLDER, os.path.basename(material['file_url']))
        if os.path.exists(filepath):
            os.remove(filepath)
        c.execute('DELETE FROM course_materials WHERE id = ?', (material_id,))
        conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': 'success'})

# ==================== 主题管理 ====================

@app.route('/api/v1/themes', methods=['GET'])
def list_themes():
    user_id = request.args.get('userId')
    conn = get_db()
    c = conn.cursor()
    c.execute('SELECT * FROM themes WHERE user_id = ? ORDER BY created_at DESC', (user_id,))
    themes = [dict(row) for row in c.fetchall()]
    conn.close()
    for t in themes: t['images'] = json.loads(t['images']) if t['images'] else []
    return jsonify({'code': 200, 'data': themes})

@app.route('/api/v1/themes', methods=['POST'])
def create_theme():
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    c.execute('''INSERT INTO themes (user_id, theme_name, theme_name_en, agent_code, agent_name, agent_name_en, images)
                 VALUES (?, ?, ?, ?, ?, ?, '[]')''',
              (data.get('userId'), data.get('themeName'), data.get('themeNameEn'),
               data.get('agentCode'), data.get('agentName'), data.get('agentNameEn')))
    theme_id = c.lastrowid
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'data': {'themeId': theme_id}})

@app.route('/api/v1/themes/<int:theme_id>', methods=['PUT'])
def update_theme(theme_id):
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    c.execute('''UPDATE themes SET theme_name=?, theme_name_en=?, agent_code=?, agent_name=?, 
                 agent_name_en=?, images=?, updated_at=CURRENT_TIMESTAMP WHERE id=?''',
              (data.get('themeName'), data.get('themeNameEn'), data.get('agentCode'),
               data.get('agentName'), data.get('agentNameEn'), json.dumps(data.get('images', [])), theme_id))
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': 'success'})

# ==================== 设备管理 ====================

@app.route('/api/v1/devices/register', methods=['POST'])
def register_device():
    data = request.get_json()
    mac_address = data.get('macAddress', '').upper()
    sn = data.get('sn') or mac_address
    
    conn = get_db()
    c = conn.cursor()
    
    c.execute('SELECT * FROM devices WHERE mac_address = ?', (mac_address,))
    existing = c.fetchone()
    
    if existing:
        # 更新最后活跃时间
        c.execute('UPDATE devices SET last_active = CURRENT_TIMESTAMP WHERE mac_address = ?', (mac_address,))
        conn.commit()
        
        c.execute('SELECT t.* FROM themes t WHERE t.id = ?', (existing['theme_id'],))
        theme = c.fetchone()
        conn.close()
        return jsonify({'code': 200, 'data': {
            'deviceId': existing['device_id'], 'sn': existing['sn'],
            'customName': existing['custom_name'], 'status': existing['status'],
            'language': existing['language'], 'theme': dict(theme) if theme else None
        }})
    
    device_id = f"dev_{uuid.uuid4().hex[:12]}"
    device_secret = f"sec_{uuid.uuid4().hex[:16]}"
    
    c.execute('''INSERT INTO devices (device_id, device_name, mac_address, sn, device_secret, status, last_active) 
                 VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)''',
              (device_id, data.get('deviceName'), mac_address, sn, device_secret, 'pending'))
    conn.commit()
    
    # 自动绑定一个未使用的License
    c.execute('SELECT * FROM licenses WHERE status = "unused" LIMIT 1')
    license = c.fetchone()
    license_info = None
    if license:
        c.execute('''UPDATE licenses SET device_id = ?, sn = ?, status = "active", bound_at = CURRENT_TIMESTAMP 
                     WHERE id = ?''', (device_id, sn, license['id']))
        c.execute('UPDATE devices SET status = "active" WHERE device_id = ?', (device_id,))
        license_info = {'license_key': license['license_key'], 'status': 'active'}
    
    conn.commit()
    conn.close()
    
    result = {'deviceId': device_id, 'deviceSecret': device_secret, 'sn': sn, 'status': 'active' if license_info else 'pending'}
    if license_info:
        result['license'] = license_info
    
    return jsonify({'code': 200, 'data': result})

# ==================== License 管理 ====================

@app.route('/api/v1/licenses', methods=['GET'])
def list_licenses():
    """获取License列表"""
    conn = get_db()
    c = conn.cursor()
    c.execute('''SELECT l.*, d.mac_address, d.device_name 
                 FROM licenses l 
                 LEFT JOIN devices d ON l.device_id = d.device_id 
                 ORDER BY l.id DESC''')
    licenses = [dict(row) for row in c.fetchall()]
    conn.close()
    return jsonify({'code': 200, 'data': licenses})

@app.route('/api/v1/licenses', methods=['POST'])
def create_license():
    """创建License (支持批量)"""
    data = request.get_json()
    licenses = data.get('licenses', [])  # 批量
    
    if not licenses and data.get('license_key'):
        licenses = [data]  # 单个
    
    conn = get_db()
    c = conn.cursor()
    created = []
    
    for lic in licenses:
        license_key = lic.get('license_key') or f"lic_{uuid.uuid4().hex[:16]}"
        product_key = lic.get('product_key', 'pk_school_ai')
        app_key = lic.get('app_key', f"app_{uuid.uuid4().hex[:6]}")
        agent_code = lic.get('agent_code', '')
        count = lic.get('count', 1)
        
        for i in range(count):
            lic_key = f"{license_key}-{i+1}" if count > 1 else license_key
            c.execute('''INSERT INTO licenses (license_key, product_key, app_key, agent_code, status) 
                         VALUES (?, ?, ?, ?, 'unused')''',
                      (lic_key, product_key, app_key, agent_code))
            created.append(lic_key)
    
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': f'Created {len(created)} licenses', 'data': created})

@app.route('/api/v1/licenses/bind', methods=['POST'])
def bind_license():
    """绑定License到设备 (设备注册时自动绑定)"""
    data = request.get_json()
    device_id = data.get('device_id')
    sn = data.get('sn')
    
    if not device_id:
        return jsonify({'code': 400, 'message': 'device_id required'}), 400
    
    conn = get_db()
    c = conn.cursor()
    
    # 查找未使用的license
    c.execute('SELECT * FROM licenses WHERE status = "unused" LIMIT 1')
    license = c.fetchone()
    
    if not license:
        conn.close()
        return jsonify({'code': 404, 'message': 'No available license'})
    
    # 绑定license到设备
    c.execute('''UPDATE licenses SET device_id = ?, sn = ?, status = "active", bound_at = CURRENT_TIMESTAMP 
                 WHERE id = ?''', (device_id, sn, license['id']))
    
    # 更新设备状态
    c.execute('UPDATE devices SET status = "active" WHERE device_id = ?', (device_id,))
    
    conn.commit()
    conn.close()
    
    return jsonify({'code': 200, 'data': {
        'license_key': license['license_key'],
        'device_id': device_id,
        'sn': sn,
        'status': 'active'
    }})

@app.route('/api/v1/licenses/batch', methods=['POST'])
def batch_import_licenses():
    """批量导入License"""
    data = request.get_json()
    keys = data.get('keys', [])  # License key列表
    product_key = data.get('product_key', 'pk_school_ai')
    
    if not keys:
        return jsonify({'code': 400, 'message': 'keys required'}), 400
    
    conn = get_db()
    c = conn.cursor()
    
    for key in keys:
        # 检查是否已存在
        c.execute('SELECT id FROM licenses WHERE license_key = ?', (key,))
        if not c.fetchone():
            c.execute('INSERT INTO licenses (license_key, product_key, status) VALUES (?, ?, "unused")',
                      (key, product_key))
    
    conn.commit()
    conn.close()
    
    return jsonify({'code': 200, 'message': f'Imported {len(keys)} licenses'})

@app.route('/api/v1/devices/bind-theme', methods=['POST'])
def bind_device_theme():
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    for mac in data.get('macAddresses', []):
        c.execute('UPDATE devices SET theme_id = ?, updated_at = CURRENT_TIMESTAMP WHERE mac_address = ?',
                  (data.get('themeId'), mac.upper()))
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': 'success'})

@app.route('/api/v1/devices', methods=['GET'])
def list_devices():
    theme_id = request.args.get('themeId')
    conn = get_db()
    c = conn.cursor()
    where = '1=1'
    params = []
    if theme_id:
        where += ' AND d.theme_id = ?'
        params.append(theme_id)
    c.execute(f'''SELECT d.*, t.theme_name, t.theme_name_en, t.agent_code, t.agent_name, t.agent_name_en, t.images
                 FROM devices d LEFT JOIN themes t ON d.theme_id = t.id WHERE {where} ORDER BY d.created_at DESC LIMIT 100''', params)
    devices = [dict(row) for row in c.fetchall()]
    conn.close()
    for d in devices: d['images'] = json.loads(d['images']) if d.get('images') else []
    return jsonify({'code': 200, 'data': devices})

# ==================== 紧急通知 ====================

@app.route('/api/v1/notifications', methods=['GET'])
def list_notifications():
    conn = get_db()
    c = conn.cursor()
    c.execute('SELECT * FROM notifications ORDER BY created_at DESC LIMIT 50')
    notifications = [dict(row) for row in c.fetchall()]
    conn.close()
    return jsonify({'code': 200, 'data': notifications})

@app.route('/api/v1/notifications', methods=['POST'])
def create_notification():
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    c.execute('''INSERT INTO notifications (title, title_en, content, content_en, priority, created_by, start_time, end_time)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)''',
              (data.get('title'), data.get('titleEn'), data.get('content'), data.get('contentEn'),
               data.get('priority', 'normal'), data.get('createdBy'), data.get('startTime'), data.get('endTime')))
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': 'success'})

@app.route('/api/v1/notifications/<int:notification_id>', methods=['DELETE'])
def delete_notification(notification_id):
    conn = get_db()
    c = conn.cursor()
    c.execute('DELETE FROM notifications WHERE id = ?', (notification_id,))
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': 'success'})

# ==================== 对话记录与分析 ====================

@app.route('/api/v1/conversations', methods=['POST'])
def save_conversation():
    """保存设备对话记录"""
    data = request.get_json()
    
    conn = get_db()
    c = conn.cursor()
    c.execute('''INSERT INTO conversations 
                 (device_id, user_query, ai_response, asr_text, tts_text, language)
                 VALUES (?, ?, ?, ?, ?, ?)''',
              (data.get('deviceId'), data.get('userQuery'), data.get('aiResponse'),
               data.get('asrText'), data.get('ttsText'), data.get('language', 'zh')))
    conversation_id = c.lastrowid
    
    # 异步调用AI分析
    # TODO: 调用AI分析服务
    
    conn.commit()
    conn.close()
    
    return jsonify({'code': 200, 'data': {'conversationId': conversation_id}})

@app.route('/api/v1/conversations', methods=['GET'])
def list_conversations():
    device_id = request.args.get('deviceId')
    keyword = request.args.get('keyword')
    start_date = request.args.get('startDate')
    end_date = request.args.get('endDate')
    page = int(request.args.get('page', 1))
    size = int(request.args.get('size', 20))
    
    conn = get_db()
    c = conn.cursor()
    
    where = '1=1'
    params = []
    if device_id:
        where += ' AND device_id = ?'
        params.append(device_id)
    if keyword:
        where += ' AND (user_query LIKE ? OR asr_text LIKE ?)'
        params.extend([f'%{keyword}%', f'%{keyword}%'])
    if start_date:
        where += ' AND created_at >= ?'
        params.append(start_date)
    if end_date:
        where += ' AND created_at <= ?'
        params.append(end_date)
    
    c.execute(f'SELECT COUNT(*) FROM conversations WHERE {where}', params)
    total = c.fetchone()[0]
    
    offset = (page - 1) * size
    c.execute(f'''SELECT * FROM conversations WHERE {where} ORDER BY created_at DESC LIMIT ? OFFSET ?''',
              params + [size, offset])
    conversations = [dict(row) for row in c.fetchall()]
    conn.close()
    
    return jsonify({'code': 200, 'data': {'total': total, 'list': conversations}})

@app.route('/api/v1/conversations/analyze', methods=['GET'])
def analyze_conversations():
    """对话数据分析"""
    device_id = request.args.get('deviceId')
    start_date = request.args.get('startDate')
    end_date = request.args.get('endDate')
    
    conn = get_db()
    c = conn.cursor()
    
    where = '1=1'
    params = []
    if device_id:
        where += ' AND device_id = ?'
        params.append(device_id)
    if start_date:
        where += ' AND created_at >= ?'
        params.append(start_date)
    if end_date:
        where += ' AND created_at <= ?'
        params.append(end_date)
    
    # 基础统计
    c.execute(f'''SELECT COUNT(*) as total, COUNT(DISTINCT device_id) as devices,
                 AVG(LENGTH(user_query)) as avg_query_len FROM conversations WHERE {where}''', params)
    basic = c.fetchone()
    
    # 热门问题
    c.execute(f'''SELECT user_query, COUNT(*) as count FROM conversations 
                 WHERE {where} AND user_query IS NOT NULL AND user_query != ''
                 GROUP BY user_query ORDER BY count DESC LIMIT 20''', params)
    hot_questions = [dict(row) for row in c.fetchall()]
    
    # 每日趋势
    c.execute(f'''SELECT DATE(created_at) as date, COUNT(*) as count FROM conversations 
                 WHERE {where} GROUP BY DATE(created_at) ORDER BY date DESC LIMIT 30''', params)
    daily_trend = [dict(row) for row in c.fetchall()]
    
    # 时段分布
    c.execute(f'''SELECT strftime('%H', created_at) as hour, COUNT(*) as count FROM conversations 
                 WHERE {where} GROUP BY hour ORDER BY hour''', params)
    hourly_dist = [dict(row) for row in c.fetchall()]
    
    # 关键词提取
    c.execute(f'''SELECT user_query FROM conversations WHERE {where} AND user_query IS NOT NULL''', params)
    all_queries = [row[0] for row in c.fetchall() if row[0]]
    
    # 简单分词统计
    keywords = {}
    zh_keywords = ['什么', '怎么', '如何', '为什么', '哪里', '哪个', '谁', '多少', '什么', '意思', '定义', '原理']
    en_keywords = ['what', 'how', 'why', 'where', 'who', 'which', 'when', 'meaning', 'define']
    for q in all_queries:
        q_lower = q.lower()
        for word in zh_keywords + en_keywords:
            if word in q_lower:
                keywords[word] = keywords.get(word, 0) + 1
    
    top_keywords = sorted(keywords.items(), key=lambda x: x[1], reverse=True)[:20]
    
    # 计算平均交互时长 (模拟)
    avg_duration = 120  # 秒
    
    # 在线设备数 (最近5分钟活跃)
    c.execute("SELECT COUNT(*) FROM devices WHERE last_active >= datetime('now', '-5 minutes')")
    online_now = c.fetchone()[0]
    
    conn.close()
    
    return jsonify({'code': 200, 'data': {
        'basic': {
            'total': basic['total'] or 0,
            'devices': basic['devices'] or 0,
            'avgQueryLen': int(basic['avg_query_len'] or 0),
            'avgDuration': avg_duration,
            'onlineNow': online_now
        },
        'hotQuestions': hot_questions,
        'dailyTrend': daily_trend,
        'hourlyDistribution': hourly_dist,
        'topKeywords': [{'keyword': k, 'count': c} for k, c in top_keywords]
    }})

# ==================== OTA ====================

@app.route('/api/v1/ota/check', methods=['GET'])
def check_ota():
    device_id = request.args.get('deviceId')
    conn = get_db()
    c = conn.cursor()
    c.execute('SELECT version_code FROM devices WHERE device_id = ?', (device_id,))
    device = c.fetchone()
    c.execute('SELECT * FROM ota_versions ORDER BY version_code DESC LIMIT 1')
    latest = c.fetchone()
    conn.close()
    
    if latest and device:
        current_version = device['version_code'] if device['version_code'] else 1
        has_update = latest['version_code'] > current_version
        
        return jsonify({'code': 200, 'data': {
            'hasUpdate': has_update,
            'versionCode': latest['version_code'],
            'versionName': latest['version_name'],
            'description': latest['description'],
            'descriptionEn': latest['description_en'],
            'downloadUrl': latest['download_url'],
            'forceUpdate': latest['force_update'] == 1
        }})
    
    return jsonify({'code': 200, 'data': {'hasUpdate': False}})

@app.route('/api/v1/ota/versions', methods=['POST'])
def create_ota_version():
    data = request.get_json()
    conn = get_db()
    c = conn.cursor()
    c.execute('''INSERT INTO ota_versions (version_code, version_name, description, description_en, download_url, force_update)
                 VALUES (?, ?, ?, ?, ?, ?)''',
              (data.get('versionCode'), data.get('versionName'), data.get('description'),
               data.get('descriptionEn'), data.get('downloadUrl'), data.get('forceUpdate', 0)))
    conn.commit()
    conn.close()
    return jsonify({'code': 200, 'message': 'success'})

# ==================== 文件上传 ====================

@app.route('/api/v1/upload', methods=['POST'])
def upload_file():
    if 'file' not in request.files:
        return jsonify({'code': 400, 'message': 'No file'}), 400
    
    file = request.files['file']
    ext = file.filename.split('.')[-1]
    filename = f"{uuid.uuid4().hex}.{ext}"
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    file.save(filepath)
    
    return jsonify({'code': 200, 'data': {'filename': filename, 'url': f'/uploads/{filename}'}})

@app.route('/uploads/<filename>')
def get_file(filename):
    return send_from_directory(UPLOAD_FOLDER, filename)

# ==================== 统计 ====================

@app.route('/api/v1/statistics', methods=['GET'])
def get_statistics():
    conn = get_db()
    c = conn.cursor()
    
    c.execute('SELECT COUNT(*) FROM devices')
    total_devices = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM devices WHERE status = ?', ('active',))
    active_devices = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM devices WHERE last_active >= datetime("now", "-5 minutes")')
    online_now = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM users WHERE role = ?', ('user',))
    total_users = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM themes')
    total_themes = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM conversations')
    total_conversations = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM notifications')
    total_notifications = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM course_materials')
    total_materials = c.fetchone()[0]
    
    # 今日统计
    c.execute('''SELECT COUNT(*) FROM conversations WHERE DATE(created_at) = DATE("now")''')
    today_conversations = c.fetchone()[0]
    
    conn.close()
    
    return jsonify({'code': 200, 'data': {
        'devices': {'total': total_devices, 'active': active_devices, 'onlineNow': online_now},
        'users': {'total': total_users},
        'themes': {'total': total_themes},
        'conversations': {'total': total_conversations, 'today': today_conversations},
        'notifications': {'total': total_notifications},
        'materials': {'total': total_materials}
    }})

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'timestamp': datetime.datetime.now().isoformat()})

@app.route('/')
@app.route('/index.html')
def admin():
    try:
        with open(os.path.join(os.path.dirname(__file__), 'templates/admin.html'), 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        return f"<html><body><h1>Error</h1><p>{str(e)}</p></body></html>"

if __name__ == '__main__':
    print("=" * 50)
    print("License 服务 - 完整版")
    print("API: http://localhost:80")
    print("管理员: admin / admin123")
    print("=" * 50)
    app.run(host='0.0.0.0', port=80, debug=False)
