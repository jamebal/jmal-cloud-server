CREATE TABLE access_token (
        id varchar(24) not null,
        access_token varchar(255),
        create_time timestamp,
        last_active_time timestamp,
        name varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE articles (
        id integer,
        public_id varchar(255) not null unique,
        alone_page boolean,
        category_ids clob,
        cover varchar(255),
        has_draft boolean,
        page_sort integer,
        is_release boolean,
        slug varchar(255),
        tag_ids clob,
        file_id bigint not null unique,
        primary key (id)
    );
CREATE TABLE category (
        id varchar(24) not null,
        category_background varchar(255),
        description varchar(255),
        is_default boolean,
        name varchar(32),
        parent_category_id varchar(24),
        slug varchar(24),
        user_id varchar(24),
        primary key (id)
    );
CREATE TABLE consumers (
        id varchar(24) not null,
        created_time timestamp,
        updated_time timestamp,
        password varchar(255),
        avatar varchar(255),
        creator boolean,
        introduction varchar(255),
        mfa_enabled boolean,
        mfa_secret TEXT,
        quota integer,
        roles clob,
        show_name varchar(255),
        slogan TEXT,
        take_up_space bigint,
        username varchar(255),
        webp_disabled boolean,
        primary key (id)
    );
CREATE TABLE direct_links (
        id varchar(24) not null,
        downloads bigint,
        file_id varchar(24),
        mark varchar(16),
        update_date timestamp,
        user_id varchar(24),
        primary key (id)
    );
CREATE TABLE file_history (
        id varchar(24) not null,
        charset varchar(255),
        compression varchar(255),
        file_id varchar(255),
        filename varchar(255),
        filepath varchar(255),
        operator varchar(255),
        size bigint,
        time varchar(255),
        upload_date timestamp,
        primary key (id)
    );
CREATE TABLE file_props (
        id integer,
        public_id varchar(255) not null unique,
        props clob,
        remark varchar(255),
        share_base boolean,
        share_id varchar(24),
        share_props clob,
        sub_share boolean,
        tags clob,
        transcode_video integer,
        primary key (id)
    );
CREATE TABLE files (
        id integer,
        public_id varchar(255) not null unique,
        content_type varchar(128),
        del_tag integer,
        etag varchar(64),
        etag_update_failed_attempts integer,
        has_content boolean not null,
        has_content_text boolean not null,
        has_html boolean not null,
        is_favorite boolean,
        is_folder boolean,
        last_etag_update_error TEXT,
        last_etag_update_request_at timestamp,
        lucene_index integer,
        md5 varchar(255),
        mount_file_id varchar(255),
        name varchar(255) not null,
        needs_etag_update boolean,
        oss_folder varchar(255),
        path varchar(255) not null,
        size bigint,
        suffix varchar(32),
        update_date timestamp not null,
        upload_date timestamp not null,
        user_id varchar(24) not null,
        props_id bigint not null unique,
        primary key (id)
    );
CREATE TABLE heartwings (
        id varchar(24) not null,
        create_time timestamp,
        creator varchar(255),
        heartwings varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE ldap_config (
        id varchar(24) not null,
        basedn varchar(255),
        roles clob,
        enable boolean,
        ldap_server varchar(255),
        login_name varchar(255),
        password varchar(255),
        userdn varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE log (
        id integer,
        public_id varchar(255) not null unique,
        browser varchar(64),
        create_time timestamp,
        device_model varchar(64),
        file_user_id varchar(24),
        filepath varchar(255),
        ip varchar(40),
        ip_info clob,
        method varchar(10),
        operating_system varchar(64),
        operation_fun varchar(96),
        operation_module varchar(24),
        remarks varchar(255),
        show_name varchar(255),
        status integer,
        time bigint,
        type varchar(16),
        url varchar(255),
        username varchar(255),
        primary key (id)
    );
CREATE TABLE menu (
        id varchar(24) not null,
        authority varchar(64),
        component varchar(255),
        create_time timestamp,
        hide boolean,
        icon varchar(255),
        menu_type integer,
        name varchar(32),
        parent_id varchar(24),
        path varchar(255),
        sort_number integer,
        update_time timestamp,
        primary key (id)
    );
CREATE TABLE ocr_config (
        id varchar(24) not null,
        created_time timestamp,
        updated_time timestamp,
        enable boolean,
        max_tasks integer check ((max_tasks<=8) and (max_tasks>=1)),
        ocr_engine varchar(255),
        primary key (id)
    );
CREATE TABLE office_config (
        id varchar(24) not null,
        created_time timestamp,
        updated_time timestamp,
        callback_server varchar(255),
        document_server varchar(255),
        format clob,
        secret varchar(255),
        token_enabled boolean,
        primary key (id)
    );
CREATE TABLE oss_config (
        id varchar(24) not null,
        created_time timestamp,
        updated_time timestamp,
        access_key varchar(255),
        bucket varchar(255),
        endpoint varchar(255),
        folder_name varchar(255),
        platform tinyint check (platform between 0 and 2),
        region varchar(255),
        secret_key varchar(255),
        user_id varchar(24),
        primary key (id)
    );
CREATE TABLE role (
        id varchar(24) not null,
        code varchar(32),
        create_time timestamp,
        menus clob,
        name varchar(32),
        remarks varchar(255),
        update_time timestamp,
        primary key (id)
    );
CREATE TABLE search_history (
        id varchar(24) not null,
        current_directory varchar(255),
        exact_search boolean,
        folder varchar(255),
        include_file_content boolean,
        include_file_name boolean,
        include_tag_name boolean,
        is_favorite boolean,
        is_folder boolean,
        keyword varchar(255),
        modify_end bigint,
        modify_start bigint,
        search_mount boolean,
        search_overall boolean,
        search_time bigint,
        size_max bigint,
        size_min bigint,
        tag_id varchar(255),
        type varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE share (
        id varchar(24) not null,
        content_type varchar(128),
        create_date timestamp,
        expire_date timestamp,
        extraction_code varchar(8),
        father_share_id varchar(24),
        file_id varchar(255),
        file_name varchar(255),
        is_folder boolean,
        is_privacy boolean,
        operation_permission_list clob,
        short_id varchar(24),
        user_id varchar(24),
        primary key (id)
    );
CREATE TABLE tag (
        id varchar(24) not null,
        color varchar(16),
        name varchar(32),
        slug varchar(32),
        sort integer,
        tag_background varchar(255),
        user_id varchar(255),
        primary key (id)
    );
CREATE TABLE transcode_config (
        id varchar(24) not null,
        created_time timestamp,
        updated_time timestamp,
        bitrate integer check ((bitrate>=100) and (bitrate<=1000000)),
        bitrate_cond integer check ((bitrate_cond<=1000000) and (bitrate_cond>=100)),
        enable boolean,
        frame_rate float check ((frame_rate<=120) and (frame_rate>=1)),
        frame_rate_cond integer check ((frame_rate_cond<=120) and (frame_rate_cond>=1)),
        height integer check ((height>=100) and (height<=10000)),
        height_cond integer check ((height_cond>=100) and (height_cond<=10000)),
        is_re_transcode boolean,
        max_threads integer check ((max_threads<=8) and (max_threads>=1)),
        vtt_thumbnail_count integer check ((vtt_thumbnail_count>=1) and (vtt_thumbnail_count<=256)),
        primary key (id)
    );
CREATE TABLE trash (
        id integer,
        public_id varchar(255) not null unique,
        content_type varchar(128),
        has_content boolean,
        hidden boolean,
        is_folder boolean,
        md5 varchar(255),
        move boolean,
        name varchar(255),
        path varchar(255),
        props clob,
        size bigint,
        suffix varchar(32),
        update_date timestamp,
        upload_date timestamp,
        user_id varchar(24),
        primary key (id)
    );
CREATE TABLE website_setting (
        id varchar(24) not null,
        alone_pages clob,
        archive_background varchar(255),
        background_desc_site varchar(255),
        background_site varchar(255),
        background_text_site varchar(255),
        category_background varchar(255),
        copyright varchar(255),
        footer_html TEXT,
        iframe TEXT,
        mfa_force_enable boolean,
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
CREATE INDEX articles_slug 
       on articles (slug);
CREATE INDEX direct_links_file_id 
       on direct_links (file_id);
CREATE INDEX direct_links_mark 
       on direct_links (mark);
CREATE INDEX file_history_links_file_id 
       on file_history (file_id);
CREATE INDEX files_name 
       on files (name);
CREATE INDEX files_size 
       on files (size);
CREATE INDEX files_is_folder 
       on files (is_folder);
CREATE INDEX files_update_date 
       on files (update_date);
CREATE INDEX files_upload_date 
       on files (upload_date);
CREATE INDEX files_user_id 
       on files (user_id);
CREATE INDEX files_path 
       on files (path);
CREATE INDEX files_mount_file_id 
       on files (mount_file_id);
CREATE INDEX files_del_tag 
       on files (del_tag);
CREATE INDEX log_create_time 
       on log (create_time);
CREATE INDEX log_username 
       on log (username);
CREATE INDEX log_ip 
       on log (ip);
CREATE INDEX log_type 
       on log (type);
CREATE INDEX search_history_user_id 
       on search_history (user_id);
CREATE INDEX search_history_search_time 
       on search_history (search_time);
CREATE INDEX search_history_keyword 
       on search_history (keyword);
CREATE INDEX share_short_id 
       on share (short_id);
CREATE INDEX trash_name 
       on trash (name);
CREATE INDEX trash_update_date 
       on trash (update_date);
CREATE INDEX trash_upload_date 
       on trash (upload_date);
CREATE INDEX trash_trash_user_id 
       on trash (user_id);
CREATE INDEX trash_path 
       on trash (path);
