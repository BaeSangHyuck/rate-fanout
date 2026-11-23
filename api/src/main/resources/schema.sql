create table product (id varchar(36) primary key, stock int not null);
create table outbox (
  event_id varchar(36) primary key,
  run_id varchar(36) not null,
  type varchar(32) not null,
  order_no int not null,
  published boolean default false not null
);
create index outbox_pending on outbox (run_id, published);
create table processed_event (event_id varchar(36) primary key, run_id varchar(36) not null);
create table coupon (id varchar(36) primary key, remaining int not null);
create table coupon_issue (coupon_id varchar(36) not null, user_id int not null, primary key (coupon_id, user_id));
