create table admin_users (
    id            bigint generated always as identity primary key,
    email         varchar(255) not null,
    password_hash varchar(255) not null,
    name          varchar(50)  not null,
    nickname      varchar(30)  not null,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create unique index admin_users_email_key on admin_users (lower(email));

create table admin_refresh_tokens (
    id         bigint generated always as identity primary key,
    admin_id   bigint      not null references admin_users (id) on delete cascade,
    token_hash varchar(64) not null unique,
    family_id  uuid        not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create index admin_refresh_tokens_admin_idx on admin_refresh_tokens (admin_id);

-- 기존 DB 업그레이드 경로: accounts의 ADMIN 역할 계정을 admin_users로 옮겨
-- 감사 컬럼(admin_id)의 귀속을 보존한다. 신규 DB에서는 ADMIN 역할이 없어 아무 일도 하지 않는다.
-- id를 그대로 유지해야 기존 admin_id가 새 FK를 통과한다.
insert into admin_users (id, email, password_hash, name, nickname)
overriding system value
select a.id, a.email, a.password_hash, a.name, a.nickname
from accounts a
join account_roles r on r.account_id = a.id and r.role = 'ADMIN'
on conflict do nothing;

-- 명시 id를 넣었으므로 identity 시퀀스를 현재 최대값 뒤로 옮긴다.
select setval(
    pg_get_serial_sequence('admin_users', 'id'),
    coalesce((select max(id) from admin_users), 0) + 1,
    false
);

-- 관리자는 별도 테이블로 분리됐으므로 감사 컬럼의 FK는 admin_users로 옮긴다.
-- ADMIN 역할이 없던 비정상 admin_id는 귀속을 포기하고 NULL로 보존해 마이그레이션이 막히지 않게 한다.
update sanctions set admin_id = null where admin_id is not null and admin_id not in (select id from admin_users);
update charges set admin_id = null where admin_id is not null and admin_id not in (select id from admin_users);
update withdrawals set admin_id = null where admin_id is not null and admin_id not in (select id from admin_users);
update inspections set admin_id = null where admin_id is not null and admin_id not in (select id from admin_users);
update reports set admin_id = null where admin_id is not null and admin_id not in (select id from admin_users);
alter table sanctions drop constraint if exists sanctions_admin_id_fkey;
alter table sanctions add constraint sanctions_admin_id_fkey foreign key (admin_id) references admin_users (id);

alter table charges drop constraint if exists charges_admin_id_fkey;
alter table charges add constraint charges_admin_id_fkey foreign key (admin_id) references admin_users (id);

alter table withdrawals drop constraint if exists withdrawals_admin_id_fkey;
alter table withdrawals add constraint withdrawals_admin_id_fkey foreign key (admin_id) references admin_users (id);

alter table inspections drop constraint if exists inspections_admin_id_fkey;
alter table inspections add constraint inspections_admin_id_fkey foreign key (admin_id) references admin_users (id);

alter table reports drop constraint if exists reports_admin_id_fkey;
alter table reports add constraint reports_admin_id_fkey foreign key (admin_id) references admin_users (id);

-- 구경로 호환 없음: accounts의 ADMIN 역할은 깨끗이 제거한다.
delete from account_roles where role = 'ADMIN';

alter table account_roles drop constraint if exists account_roles_role_check;
alter table account_roles add constraint account_roles_role_check
    check (role in ('MEMBER', 'HOST'));
