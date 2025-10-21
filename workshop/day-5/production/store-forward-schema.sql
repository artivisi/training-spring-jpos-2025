-- Store-and-Forward Schema for Day 5: Connection Resiliency
-- Tables for offline transaction queue and retry management

-- Transaction Queue for store-and-forward pattern
CREATE TABLE transaction_queue (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) UNIQUE NOT NULL,
    queue_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PROCESSING, SUCCESS, FAILED, EXPIRED
    priority INTEGER DEFAULT 5,                  -- 1=Highest, 10=Lowest
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '7 days'),

    -- Request data
    service_type VARCHAR(50) NOT NULL,            -- ACQUIRER, GATEWAY, BILLING
    endpoint_url VARCHAR(255),
    http_method VARCHAR(10),
    request_headers JSONB,
    request_body TEXT,
    request_size_bytes INTEGER,

    -- Response data
    response_status_code INTEGER,
    response_headers JSONB,
    response_body TEXT,
    response_size_bytes INTEGER,

    -- Error information
    error_code VARCHAR(50),
    error_message TEXT,
    error_category VARCHAR(50),                  -- NETWORK, BUSINESS, SYSTEM, TIMEOUT
    is_transient_error BOOLEAN DEFAULT false,     -- Whether error is retryable

    -- Processing metadata
    processing_duration_ms INTEGER,
    total_retry_time_ms INTEGER,
    correlation_id VARCHAR(128),
    client_id VARCHAR(64),
    client_ip INET,

    -- Business context
    bill_id VARCHAR(64),
    customer_id VARCHAR(64),
    amount DECIMAL(15,2),
    transaction_type VARCHAR(50),
    urgency_level VARCHAR(20) DEFAULT 'NORMAL'   -- LOW, NORMAL, HIGH, CRITICAL
);

-- Retry Configuration
CREATE TABLE retry_configuration (
    id BIGSERIAL PRIMARY KEY,
    service_type VARCHAR(50) NOT NULL,
    error_category VARCHAR(50) NOT NULL,
    max_retries INTEGER NOT NULL,
    backoff_strategy VARCHAR(50) DEFAULT 'EXPONENTIAL',  -- LINEAR, EXPONENTIAL, FIXED
    initial_backoff_ms INTEGER NOT NULL,
    max_backoff_ms INTEGER NOT NULL,
    backoff_multiplier DECIMAL(3,2) DEFAULT 2.0,
    jitter_enabled BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(service_type, error_category)
);

-- Circuit Breaker Status
CREATE TABLE circuit_breaker_status (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    state VARCHAR(20) DEFAULT 'CLOSED',           -- CLOSED, OPEN, HALF_OPEN
    failure_count INTEGER DEFAULT 0,
    failure_threshold INTEGER DEFAULT 5,
    success_threshold INTEGER DEFAULT 3,
    timeout_ms INTEGER DEFAULT 60000,
    last_failure_at TIMESTAMP,
    last_success_at TIMESTAMP,
    next_attempt_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(service_name, endpoint)
);

-- Connection Pool Metrics
CREATE TABLE connection_pool_metrics (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    pool_name VARCHAR(100) NOT NULL,
    total_connections INTEGER DEFAULT 0,
    active_connections INTEGER DEFAULT 0,
    idle_connections INTEGER DEFAULT 0,
    waiting_threads INTEGER DEFAULT 0,
    max_pool_size INTEGER DEFAULT 20,
    min_idle_connections INTEGER DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(service_name, pool_name)
);

-- System Health Metrics
CREATE TABLE system_health_metrics (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,            -- CPU, MEMORY, DISK, NETWORK
    metric_value DECIMAL(10,2) NOT NULL,
    metric_unit VARCHAR(20),                     -- percent, bytes, ms, count
    threshold_warning DECIMAL(10,2),
    threshold_critical DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'OK',             -- OK, WARNING, CRITICAL
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Batch Processing Jobs
CREATE TABLE batch_processing_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_type VARCHAR(50) NOT NULL,               -- QUEUE_PROCESSOR, CLEANUP, METRICS_AGGREGATOR
    status VARCHAR(20) DEFAULT 'SCHEDULED',     -- SCHEDULED, RUNNING, COMPLETED, FAILED
    scheduled_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,

    -- Job parameters
    parameters JSONB,

    -- Processing results
    records_processed INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    records_skipped INTEGER DEFAULT 0,

    -- Error information
    error_message TEXT,
    error_details JSONB,

    -- Job configuration
    cron_expression VARCHAR(100),
    is_enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Dead Letter Queue for permanently failed transactions
CREATE TABLE dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    original_transaction_id VARCHAR(64) NOT NULL,
    queue_entry_id BIGINT REFERENCES transaction_queue(id),
    failure_reason TEXT NOT NULL,
    final_error_code VARCHAR(50),
    final_error_category VARCHAR(50),
    total_attempts INTEGER NOT NULL,
    total_processing_time_ms INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Original request data preserved for manual analysis
    original_request TEXT,
    original_headers JSONB,
    original_metadata JSONB,

    -- Resolution information
    resolution_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, RESOLVED, IGNORED
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    resolved_at TIMESTAMP
);

-- Queue Performance Statistics
CREATE VIEW queue_performance_stats AS
SELECT
    DATE_TRUNC('hour', created_at) as hour_bucket,
    service_type,
    queue_status,
    COUNT(*) as transaction_count,
    AVG(processing_duration_ms) as avg_processing_time,
    MAX(processing_duration_ms) as max_processing_time,
    SUM(CASE WHEN retry_count > 0 THEN 1 ELSE 0 END) as retry_count,
    AVG(retry_count) as avg_retry_count,
    MAX(retry_count) as max_retry_count,
    SUM(CASE WHEN error_category = 'NETWORK' THEN 1 ELSE 0 END) as network_errors,
    SUM(CASE WHEN error_category = 'TIMEOUT' THEN 1 ELSE 0 END) as timeout_errors,
    SUM(CASE WHEN error_category = 'BUSINESS' THEN 1 ELSE 0 END) as business_errors
FROM transaction_queue
WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY DATE_TRUNC('hour', created_at), service_type, queue_status
ORDER BY hour_bucket DESC, service_type, queue_status;

-- Queue Health Summary
CREATE VIEW queue_health_summary AS
SELECT
    service_type,
    COUNT(*) as total_transactions,
    SUM(CASE WHEN queue_status = 'PENDING' THEN 1 ELSE 0 END) as pending_count,
    SUM(CASE WHEN queue_status = 'PROCESSING' THEN 1 ELSE 0 END) as processing_count,
    SUM(CASE WHEN queue_status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN queue_status = 'FAILED' THEN 1 ELSE 0 END) as failed_count,
    SUM(CASE WHEN queue_status = 'EXPIRED' THEN 1 ELSE 0 END) as expired_count,
    ROUND(AVG(processing_duration_ms), 2) as avg_processing_time,
    ROUND(AVG(retry_count), 2) as avg_retry_count,
    MAX(retry_count) as max_retry_count,
    MIN(next_retry_at) as next_retry_at,
    COUNT(CASE WHEN next_retry_at <= CURRENT_TIMESTAMP AND queue_status = 'PENDING' THEN 1 END) as overdue_count
FROM transaction_queue
GROUP BY service_type;

-- Create indexes for performance
CREATE INDEX idx_transaction_queue_status ON transaction_queue(queue_status);
CREATE INDEX idx_transaction_queue_service ON transaction_queue(service_type);
CREATE INDEX idx_transaction_queue_priority ON transaction_queue(priority);
CREATE INDEX idx_transaction_queue_next_retry ON transaction_queue(next_retry_at) WHERE queue_status = 'PENDING';
CREATE INDEX idx_transaction_queue_created_at ON transaction_queue(created_at);
CREATE INDEX idx_transaction_queue_expires_at ON transaction_queue(expires_at);
CREATE INDEX idx_transaction_queue_transaction_id ON transaction_queue(transaction_id);
CREATE INDEX idx_transaction_queue_correlation_id ON transaction_queue(correlation_id);
CREATE INDEX idx_transaction_queue_bill_customer ON transaction_queue(bill_id, customer_id);
CREATE INDEX idx_transaction_queue_error_category ON transaction_queue(error_category);
CREATE INDEX idx_transaction_queue_urgency ON transaction_queue(urgency_level);

CREATE INDEX idx_retry_configuration_active ON retry_configuration(is_active);
CREATE INDEX idx_circuit_breaker_status ON circuit_breaker_status(state);
CREATE INDEX idx_circuit_breaker_updated ON circuit_breaker_status(updated_at);

CREATE INDEX idx_system_health_metrics_service_type ON system_health_metrics(service_name, metric_type);
CREATE INDEX idx_system_health_metrics_created_at ON system_health_metrics(created_at);
CREATE INDEX idx_system_health_metrics_status ON system_health_metrics(status);

CREATE INDEX idx_batch_jobs_status ON batch_processing_jobs(status);
CREATE INDEX idx_batch_jobs_scheduled ON batch_processing_jobs(scheduled_at);
CREATE INDEX idx_batch_jobs_type ON batch_processing_jobs(job_type);

CREATE INDEX idx_dead_letter_queue_status ON dead_letter_queue(resolution_status);
CREATE INDEX idx_dead_letter_queue_created_at ON dead_letter_queue(created_at);

-- Create trigger for updating updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_retry_configuration_updated_at
    BEFORE UPDATE ON retry_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_circuit_breaker_status_updated_at
    BEFORE UPDATE ON circuit_breaker_status
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_batch_processing_jobs_updated_at
    BEFORE UPDATE ON batch_processing_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default retry configurations
INSERT INTO retry_configuration (service_type, error_category, max_retries, backoff_strategy, initial_backoff_ms, max_backoff_ms, backoff_multiplier) VALUES
('ACQUIRER', 'NETWORK', 5, 'EXPONENTIAL', 1000, 30000, 2.0),
('ACQUIRER', 'TIMEOUT', 3, 'EXPONENTIAL', 2000, 30000, 2.0),
('ACQUIRER', 'BUSINESS', 0, 'FIXED', 0, 0, 1.0),
('ACQUIRER', 'SYSTEM', 3, 'LINEAR', 5000, 15000, 1.5),

('GATEWAY', 'NETWORK', 5, 'EXPONENTIAL', 1000, 30000, 2.0),
('GATEWAY', 'TIMEOUT', 3, 'EXPONENTIAL', 2000, 30000, 2.0),
('GATEWAY', 'BUSINESS', 0, 'FIXED', 0, 0, 1.0),
('GATEWAY', 'SYSTEM', 3, 'LINEAR', 5000, 15000, 1.5),

('BILLING', 'NETWORK', 5, 'EXPONENTIAL', 1000, 30000, 2.0),
('BILLING', 'TIMEOUT', 3, 'EXPONENTIAL', 2000, 30000, 2.0),
('BILLING', 'BUSINESS', 1, 'FIXED', 0, 0, 1.0),
('BILLING', 'SYSTEM', 3, 'LINEAR', 5000, 15000, 1.5);

-- Insert initial circuit breaker status records
INSERT INTO circuit_breaker_status (service_name, endpoint, failure_threshold, success_threshold, timeout_ms) VALUES
('Acquirer Service', '/api/v1/payment/request', 5, 3, 60000),
('Acquirer Service', '/api/v1/payment/status', 5, 3, 30000),
('Gateway Service', '/api/iso/payment', 5, 3, 45000),
('Billing Service', '/api/bill/payment', 5, 3, 60000),
('HSM Service', '/api/hsm/pin/encrypt', 3, 2, 30000),
('HSM Service', '/api/hsm/mac/generate', 3, 2, 20000);

-- Create function for queue cleanup (expired transactions)
CREATE OR REPLACE FUNCTION cleanup_expired_queue_entries()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM transaction_queue
    WHERE expires_at < CURRENT_TIMESTAMP
    AND queue_status IN ('PENDING', 'FAILED');

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    -- Log the cleanup
    INSERT INTO batch_processing_jobs (
        job_name, job_type, status, completed_at, duration_ms,
        records_processed, parameters
    ) VALUES (
        'queue-cleanup', 'CLEANUP', 'COMPLETED', CURRENT_TIMESTAMP, 0,
        deleted_count, jsonb_build_object('deleted_count', deleted_count)
    );

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Create function for retry queue processing
CREATE OR REPLACE FUNCTION process_retry_queue(max_batch_size INTEGER DEFAULT 100)
RETURNS TABLE(processing_result TEXT, transaction_id VARCHAR(64), queue_id BIGINT) AS $$
BEGIN
    RETURN QUERY
    UPDATE transaction_queue
    SET queue_status = 'PROCESSING',
        last_attempt_at = CURRENT_TIMESTAMP,
        retry_count = retry_count + 1
    WHERE id IN (
        SELECT id FROM transaction_queue
        WHERE queue_status = 'PENDING'
        AND next_retry_at <= CURRENT_TIMESTAMP
        ORDER BY priority ASC, created_at ASC
        LIMIT max_batch_size
        FOR UPDATE SKIP LOCKED
    )
    RETURNING
        CASE
            WHEN retry_count >= max_retries THEN 'MAX_RETRIES_REACHED'
            ELSE 'READY_FOR_PROCESSING'
        END,
        transaction_id,
        id;
END;
$$ LANGUAGE plpgsql;