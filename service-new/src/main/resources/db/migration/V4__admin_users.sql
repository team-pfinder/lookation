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

-- 관리자는 별도 테이블로 분리됐으므로 감사 컬럼의 FK는 admin_users로 옮긴다.
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
