CREATE TABLE access_token (
        create_time timestamp,
        created_time timestamp,
        last_active_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        access_token varchar(255),
        name varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE articles (
        alone_page boolean,
        is_release boolean,
        page_sort integer,
        created_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        category_ids clob,
        cover varchar(255),
        draft clob,
        file_id varchar(24) not null unique,
        slug varchar(255),
        tag_ids clob,
        primary key (id)
    );
CREATE TABLE category (
        is_default boolean,
        created_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        category_background varchar(255),
        description varchar(255),
        name varchar(255),
        parent_category_id varchar(255),
        slug varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE consumers (
        creator boolean,
        mfa_enabled boolean,
        quota integer,
        webp_disabled boolean,
        created_time timestamp,
        take_up_space bigint,
        updated_time timestamp,
        id varchar(24) not null,
        avatar varchar(255),
        introduction varchar(255),
        mfa_secret TEXT,
        password varchar(255),
        roles clob,
        show_name varchar(255),
        slogan varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE direct_links (
        created_time timestamp,
        downloads bigint,
        update_date timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        file_id varchar(255),
        mark varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE file_props (
        blob_type tinyint check (blob_type between 0 and 2),
        del_tag integer,
        lucene_index integer,
        share_base boolean,
        sub_share boolean,
        blob_data blob,
        id varchar(255) not null,
        props clob,
        share_id varchar(255),
        share_props clob,
        tags clob,
        primary key (id)
    );
CREATE TABLE files (
        etag_update_failed_attempts integer,
        is_favorite boolean,
        is_folder boolean,
        needs_etag_update boolean,
        created_time timestamp,
        last_etag_update_request_at timestamp,
        size bigint,
        update_date timestamp,
        updated_time timestamp,
        upload_date timestamp,
        user_id varchar(24),
        etag varchar(64),
        content_type varchar(255),
        id varchar(24) not null,
        last_etag_update_error varchar(255),
        mount_file_id varchar(255),
        name varchar(255),
        path varchar(255),
        suffix varchar(255),
        primary key (id)
    );
CREATE TABLE heartwings (
        create_time timestamp,
        created_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        creator varchar(255),
        heartwings varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE ldap_config (
        enable boolean,
        created_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        basedn varchar(255),
        ldap_server varchar(255),
        login_name varchar(255),
        password varchar(255),
        roles clob,
        user_id varchar(255),
        userdn varchar(255),
        primary key (id)
    );
CREATE TABLE log (
        status integer,
        create_time timestamp,
        created_time timestamp,
        time bigint,
        updated_time timestamp,
        id varchar(24) not null,
        browser varchar(255),
        device_model varchar(255),
        file_user_id varchar(255),
        filepath varchar(255),
        ip varchar(255),
        ip_info clob,
        method varchar(255),
        operating_system varchar(255),
        operation_fun varchar(255),
        operation_module varchar(255),
        remarks varchar(255),
        show_name varchar(255),
        type varchar(255),
        url varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE menu (
        hide boolean,
        menu_type integer,
        sort_number integer,
        create_time timestamp,
        created_time timestamp,
        update_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        authority varchar(255),
        component varchar(255),
        icon varchar(255),
        name varchar(255),
        parent_id varchar(255),
        path varchar(255),
        primary key (id)
    );
CREATE TABLE role (
        create_time timestamp,
        created_time timestamp,
        update_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        code varchar(255),
        menus clob,
        name varchar(255),
        remarks varchar(255),
        primary key (id)
    );
CREATE TABLE share (
        is_folder boolean,
        is_privacy boolean,
        operation_permission_list blob,
        share_base boolean,
        create_date timestamp,
        created_time timestamp,
        expire_date timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        content_type varchar(255),
        extraction_code varchar(255),
        father_share_id varchar(255),
        file_id varchar(255),
        file_name varchar(255),
        short_id varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE tag (
        sort integer,
        created_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        color varchar(255),
        name varchar(255),
        slug varchar(255),
        tag_background varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE trash (
        blob_type tinyint check (blob_type between 0 and 2),
        hidden boolean,
        is_folder boolean,
        move boolean,
        created_time timestamp,
        size bigint,
        update_date timestamp,
        updated_time timestamp,
        upload_date timestamp,
        id varchar(24) not null,
        blob_data blob,
        content_type varchar(255),
        md5 varchar(255),
        name varchar(255),
        path varchar(255),
        props clob,
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE website_setting (
        mfa_force_enable boolean,
        created_time timestamp,
        updated_time timestamp,
        id varchar(24) not null,
        archive_background varchar(255),
        background_desc_site varchar(255),
        background_site varchar(255),
        background_text_site varchar(255),
        category_background varchar(255),
        copyright varchar(255),
        footer_html TEXT,
        iframe TEXT,
        netdisk_logo varchar(255),
        netdisk_name varchar(255),
        network_record_number varchar(255),
        network_record_number_str varchar(255),
        operating_buttons TEXT,
        record_permission_num varchar(255),
        site_ico varchar(255),
        site_logo varchar(255),
        site_name varchar(255),
        site_url varchar(255),
        tag_background varchar(255),
        primary key (id)
    );
CREATE INDEX idx_name
       on files (name);
CREATE INDEX idx_size
       on files (size);
CREATE INDEX idx_update_date
       on files (update_date);
CREATE INDEX idx_path_name
       on files (path, name);
CREATE INDEX idx_user_md5_path
       on files (user_id, path);
CREATE INDEX idx_user_path
       on files (user_id, path);
CREATE INDEX idx_user_path_name
       on files (user_id, path, name);
CREATE INDEX idx_user_is_folder_path
       on files (user_id, is_folder, path);
CREATE INDEX idx_user_is_folder_path_name
       on files (user_id, is_folder, path, name);
CREATE INDEX idx_user_is_folder
       on files (user_id, is_folder);
CREATE INDEX idx_user_is_favorite
       on files (user_id, is_favorite);
CREATE INDEX idx_user_content_type
       on files (user_id, content_type);
