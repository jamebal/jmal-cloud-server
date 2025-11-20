-- 创建 user_groups 表
CREATE TABLE "user_groups" (
                               "id" varchar(24) NOT NULL,
                               "created_time" timestamp(6),
                               "updated_time" timestamp(6),
                               "code" varchar(32) NOT NULL,
                               "name" varchar(64) NOT NULL,
                               "description" text,
                               "roles" jsonb,
                               PRIMARY KEY ("id"),
                               CONSTRAINT "uk_user_groups_code" UNIQUE ("code")
);

COMMENT ON TABLE "user_groups" IS '用户组表';
COMMENT ON COLUMN "user_groups"."code" IS '组标识';
COMMENT ON COLUMN "user_groups"."name" IS '组名';
COMMENT ON COLUMN "user_groups"."roles" IS '角色ID列表';

-- 在 consumers 表中添加 groups 字段
ALTER TABLE "consumers" ADD COLUMN "groups" jsonb;

COMMENT ON COLUMN "consumers"."groups" IS '用户组ID列表';
