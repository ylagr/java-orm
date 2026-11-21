-- 商城分类表
CREATE TABLE IF NOT EXISTS mall_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    sort INT DEFAULT 0,
    icon VARCHAR(255),
    deleted INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商城商品表 (演示 @Version 乐观锁, @TableLogic 逻辑删除)
CREATE TABLE IF NOT EXISTS mall_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    title VARCHAR(255),
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    sales INT NOT NULL DEFAULT 0,
    image VARCHAR(255),
    status INT DEFAULT 1 COMMENT '1-上架, 0-下架',
    version INT DEFAULT 1 COMMENT '乐观锁版本号',
    deleted INT DEFAULT 0 COMMENT '0-未删除, 1-已删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商城用户/会员表
CREATE TABLE IF NOT EXISTS mall_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    nickname VARCHAR(64),
    phone VARCHAR(32),
    avatar VARCHAR(255),
    status INT DEFAULT 1 COMMENT '1-启用, 0-禁用',
    points INT DEFAULT 0,
    balance DECIMAL(10, 2) DEFAULT 0.00,
    deleted INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商城订单表
CREATE TABLE IF NOT EXISTS mall_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    pay_amount DECIMAL(10, 2) NOT NULL,
    status INT DEFAULT 10 COMMENT '10-待付款, 20-待发货, 30-已发货, 40-已完成, 50-已取消',
    receiver_name VARCHAR(64),
    receiver_phone VARCHAR(32),
    receiver_address VARCHAR(255),
    version INT DEFAULT 1,
    deleted INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pay_time TIMESTAMP NULL
);

-- 商城订单明细表
CREATE TABLE IF NOT EXISTS mall_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_image VARCHAR(255),
    price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    total_amount DECIMAL(10, 2) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
