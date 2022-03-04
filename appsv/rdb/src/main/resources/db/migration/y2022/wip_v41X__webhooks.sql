

-- New domains
-------------------------------------------------

create domain i32_lt2e9_d i32_d;
alter  domain i32_lt2e9_d add
   constraint i32_lt2e9_d_c_lt_2e9 check (value < 2000000000);

create domain i32_abs_lt2e9_d i32_lt2e9_d;
alter  domain i32_abs_lt2e9_d add
   constraint i32_abs_lt2e9_d_c_gt_m2e9 check (value > -2000000000);

create domain i32_abs_lt2e9_nz_d i32_abs_lt2e9_d;
alter  domain i32_abs_lt2e9_nz_d add
   constraint i32_abs_lt2e9_nz_d_c_nz check (value <> 0);

create domain i32_lt2e9_gz_d i32_lt2e9_d;
alter  domain i32_lt2e9_gz_d add
   constraint i32_lt2e9_gz_d_c_gz check (value > 0);

create domain i32_lt2e9_gt1000_d i32_lt2e9_d;
alter  domain i32_lt2e9_gt1000_d add
   constraint i32_lt2e9_gt1000_d_c_gt1000 check (value > 1000);


create domain page_id_st_d text_nonempty_ste60_d;
alter  domain page_id_st_d add
   constraint page_id_st_d_c_chars check (value ~ '^[a-zA-Z0-9_]*$');

create domain page_id_d__later  i64_gz_d;

create domain site_id_d     i32_abs_lt2e9_nz_d;
create domain cat_id_d      i32_lt2e9_gz_d;
create domain tagtype_id_d  i32_lt2e9_gt1000_d;

create domain pat_id_d      i32_abs_lt2e9_nz_d;

create domain member_id_d   pat_id_d;
alter  domain member_id_d add
   constraint member_id_d_c_gtz check (value > 0);


create domain webhook_id_d   i32_lt2e9_gt1000_d;
create domain event_type_d   i16_gz_d;  -- for now
create domain event_id_d     i32_lt2e9_gt1000_d;



-- Webhooks
-------------------------------------------------

-- Hmm, let's enable, if API enabled?
-- alter table settings3 add column enable_webhooks_c bool;


create table webhooks_t (
  site_id_c  int,  -- pk
  webhook_id_c webhook_id_d,  -- pk

  owner_id_c  pat_id_d,   -- who may edit this webhook conf. Null = admins only.
  run_as_id_c  pat_id_d,  -- only sends events about things that run_as_id_c can see

  enabled_c  bool not null,
  broken_c  bool not null,
  deleted_c  bool not null,
  
  descr_c  text_nonempty_ste120_trimmed_d,
  send_to_url_c  http_url_d not null,
  check_dest_cert_c  bool,   -- if should check the TLS cert of the send_to_url_c.
  send_event_types_c  int[], -- event_type_d[],
  send_format_v_c  i16_gz_d not null,
  send_max_per_sec_c  i16_gz_d not null,

  sent_up_to_when_c  timestamp,
  sent_up_to_event_id_c  event_id_d,
  maybe_pending_min_c  i16_gz_d not null,
  retry_event_ids_c  int[], -- not?: event_id_d[],

  constraint webhooks_p_id primary key (site_id_c, webhook_id_c),

  constraint webhooks_r_sites foreign key (site_id_c) references sites3 (id),

  -- fk ix: webhooks_i_ownerid
  constraint webhooks_ownerid_r_pats foreign key (site_id_c, owner_id_c)
      references users3 (site_id, user_id),

  -- fk ix: webhooks_i_runasid
  constraint webhooks_runasid_r_pats foreign key (site_id_c, run_as_id_c)
      references users3 (site_id, user_id),

  constraint webhooks_c_formatv check (send_format_v_c = 1)
);


create index webhooks_i_ownerid on webhooks_t (site_id_c, owner_id_c);
create index webhooks_i_runasid on webhooks_t (site_id_c, run_as_id_c);



create table webhooks_sent_t (
  site_id_c  int,  -- pk
  webhook_id_c  webhook_id_d,  -- pk
  event_id_c  event_id_d,  -- pk
  attempt_nr_c  i16_gz_d,  -- pk

  sent_to_url_c  http_url_d not null,
  sent_at_c  timestamp not null,
  sent_types_c  int[],  -- event_type_d[],
  sent_by_app_ver_c  text not null,
  sent_format_v_c  i16_gz_d not null,
  sent_json_c  jsonb not null,
  sent_headers_c  jsonb not null, -- hmm?

  send_failed_at_c  timestamp,
  send_failed_type_c  int,
  send_failed_msg_c  text,

  resp_at_c timestamp,
  resp_status_c i16_gz_d,
  resp_body_c text_nonempty_ste2100_d,


  constraint whooksent_p primary key (site_id_c, webhook_id_c, event_id_c, attempt_nr_c),

  -- fk ix: pk
  constraint whooksent_whookid_r_whooks foreign key (site_id_c, webhook_id_c)
    references webhooks_t (site_id_c, webhook_id_c)
);



--  eventId:  webhook_id + type + post_id + when + subcount
--  eventType:   webhook_type_d
--  dataUnencr: {
--  }
--  dataAsPaseto:  {
--    pageId: __
--    postId: __
--    postNr: __
--    postSource: __
--    categoryExtId: __
--    authorExtId: __
--    
-- }



 "dw2_cats_parent_slug__u" UNIQUE, btree (site_id, parent_id, slug) 
   -- enforce lazily, + softw version in name?
