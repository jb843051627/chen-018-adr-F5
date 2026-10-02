-- H2（MODE=MySQL）下的对照册领域测试库；列与 doc/schema/adr.sql 对齐。
DROP TABLE IF EXISTS t_adr_report_row;
DROP TABLE IF EXISTS t_adr_signal_line;
DROP TABLE IF EXISTS t_adr_review_sign;
DROP TABLE IF EXISTS t_adr_review_bill;
DROP TABLE IF EXISTS t_sys_notice_user;
DROP TABLE IF EXISTS t_sys_notice;
DROP TABLE IF EXISTS t_sys_user;
DROP TABLE IF EXISTS t_adr_cycle_notice;
DROP TABLE IF EXISTS t_adr_cycle_send;
DROP TABLE IF EXISTS t_adr_cycle_calendar;
DROP TABLE IF EXISTS t_adr_cycle_task;
DROP TABLE IF EXISTS t_adr_check_booklet;
DROP TABLE IF EXISTS t_adr_check_period;
DROP TABLE IF EXISTS t_adr_term_card;
DROP TABLE IF EXISTS t_adr_case_flow;
DROP TABLE IF EXISTS t_adr_org_arch;

CREATE TABLE t_adr_case_flow (
  id bigint PRIMARY KEY,
  biz_no varchar(64),
  stage int,
  status int,
  content varchar(255),
  last_action varchar(64),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_caseflow_bill ON t_adr_case_flow (biz_no, del_flag);

CREATE TABLE t_adr_org_arch (
  id bigint PRIMARY KEY,
  site_no varchar(64),
  site_name varchar(128),
  site_type varchar(32),
  road_name varchar(128),
  th1_max decimal(12,2),
  th2_max decimal(12,2),
  th3_max decimal(12,2),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_adr_term_card (
  id bigint PRIMARY KEY,
  bill_no varchar(64),
  node_no int,
  site_id int,
  site_no varchar(64),
  qty decimal(12,2),
  fine_amt decimal(12,2),
  content varchar(255),
  grade_level int,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_adr_check_period (
  id bigint PRIMARY KEY,
  period_no varchar(32) NOT NULL,
  carry_in_qty decimal(12,2) NOT NULL DEFAULT 0,
  used_qty decimal(12,2) NOT NULL DEFAULT 0,
  closed int NOT NULL DEFAULT 0,
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_period UNIQUE (period_no)
);

CREATE TABLE t_adr_check_booklet (
  id bigint PRIMARY KEY,
  batch_no varchar(64) NOT NULL,
  period_no varchar(32),
  row_cap int NOT NULL DEFAULT 500,
  total_rows int NOT NULL DEFAULT 0,
  linked_rows int NOT NULL DEFAULT 0,
  suspended_rows int NOT NULL DEFAULT 0,
  void_flag int DEFAULT 0,
  receipt_status int NOT NULL DEFAULT 0,
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_batch_live UNIQUE (batch_no, void_flag)
);

CREATE TABLE t_adr_cycle_task (
  id bigint PRIMARY KEY,
  item_no varchar(64),
  site_no varchar(64),
  biz_type varchar(32),
  due_at datetime,
  amount decimal(12,2),
  ref_doc varchar(128),
  contact_mobile varchar(32),
  watcher_no varchar(64),
  sent_at datetime,
  fail_reason varchar(255),
  withdraw_at datetime,
  content varchar(255),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_cycle_status_due ON t_adr_cycle_task (status, del_flag, due_at);

CREATE TABLE t_adr_cycle_send (
  id bigint PRIMARY KEY,
  round_no varchar(64) NOT NULL,
  item_id bigint NOT NULL,
  item_no varchar(64),
  site_no varchar(64),
  action varchar(16) NOT NULL,
  status_after int,
  acted_at datetime,
  note varchar(255),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_send_round ON t_adr_cycle_send (round_no);
CREATE INDEX idx_send_item ON t_adr_cycle_send (item_id);

CREATE TABLE t_adr_cycle_notice (
  id bigint PRIMARY KEY,
  send_id bigint NOT NULL,
  item_id bigint NOT NULL,
  channel varchar(16) NOT NULL,
  delivered int NOT NULL DEFAULT 0,
  delivered_at datetime,
  target varchar(128),
  fail_note varchar(255),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_notice_send ON t_adr_cycle_notice (send_id);

CREATE TABLE t_adr_cycle_calendar (
  id bigint PRIMARY KEY,
  holiday date NOT NULL,
  day_name varchar(64),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_holiday UNIQUE (holiday)
);

CREATE TABLE t_adr_report_row (
  id bigint PRIMARY KEY,
  booklet_id bigint NOT NULL,
  batch_no varchar(64),
  period_no varchar(32),
  row_no int,
  item_code varchar(64),
  qty decimal(12,2),
  posted_qty decimal(12,2),
  suspend_reason varchar(255),
  archive_phase int NOT NULL DEFAULT 0,
  cross_count int NOT NULL DEFAULT 0,
  archive_time datetime,
  status int,
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_booklet_row UNIQUE (booklet_id, row_no)
);
CREATE INDEX idx_code_phase ON t_adr_report_row (item_code, archive_phase, del_flag);

CREATE TABLE t_adr_review_bill (
  id bigint PRIMARY KEY,
  bill_no varchar(64),
  opener varchar(64),
  round_no int NOT NULL DEFAULT 1,
  node_no int,
  sign_mode int,
  need_count int,
  sign_count int,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_review_bill_no UNIQUE (bill_no)
);

CREATE TABLE t_adr_review_sign (
  id bigint PRIMARY KEY,
  bill_no varchar(64) NOT NULL,
  round_no int NOT NULL DEFAULT 1,
  node_no int NOT NULL,
  sign_mode int NOT NULL,
  signer varchar(64),
  action_kind int NOT NULL DEFAULT 0,
  comment varchar(500),
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_review_sign_bill ON t_adr_review_sign (bill_no, round_no, del_flag);
CREATE INDEX idx_review_sign_node ON t_adr_review_sign (bill_no, round_no, node_no, del_flag);

-- 信号检出限值线：列与 doc/schema/adr.sql 对齐
CREATE TABLE t_adr_signal_line (
  id bigint PRIMARY KEY,
  rule_code varchar(64),
  rule_name varchar(128),
  th1_max decimal(12,2),
  th2_max decimal(12,2),
  th3_max decimal(12,2),
  eff_start datetime,
  eff_end datetime,
  priority int,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_signal_line_code ON t_adr_signal_line (rule_code, del_flag);

-- 站内消息通道测试用：用户/公告/公告用户挂接
CREATE TABLE t_sys_user (
  id bigint PRIMARY KEY,
  username varchar(255),
  password varchar(255),
  nickname varchar(255),
  user_type varchar(2),
  email varchar(50),
  phonenumber varchar(32),
  sex char(1),
  avatar varchar(100),
  dep_id bigint,
  pos_id bigint,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_sys_notice (
  id bigint PRIMARY KEY,
  title varchar(255),
  content varchar(1000),
  type int,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_sys_notice_user (
  id bigint PRIMARY KEY,
  notice_id bigint,
  user_id bigint,
  state bigint,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
