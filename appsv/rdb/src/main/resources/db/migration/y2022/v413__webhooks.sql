

-- New domains
-------------------------------------------------

create domain text_nonempty_ste250_d text_nonempty_inf_d;
alter domain  text_nonempty_ste250_d add
   constraint text_nonempty_ste250_d_c_ste250 check (length(value) <= 250);

create domain text_nonempty_ste250_trimmed_d text_nonempty_ste250_d;
alter domain  text_nonempty_ste250_trimmed_d add
   constraint text_nonempty_ste250_trimmed_d_c_trimmed check (is_trimmed(value));

create domain text_nonempty_ste500_d text_nonempty_inf_d;
alter domain  text_nonempty_ste500_d add
   constraint text_nonempty_ste500_d_c_ste500 check (length(value) <= 500);

create domain text_nonempty_ste500_trimmed_d text_nonempty_ste500_d;
alter domain  text_nonempty_ste500_trimmed_d add
   constraint text_nonempty_ste500_trimmed_d_c_trimmed check (is_trimmed(value));

create domain text_nonempty_ste1000_d text_nonempty_inf_d;
alter domain  text_nonempty_ste1000_d add
   constraint text_nonempty_ste1000_d_c_ste1000 check (length(value) <= 1000);

create domain text_nonempty_ste1000_trimmed_d text_nonempty_ste1000_d;
alter domain  text_nonempty_ste1000_trimmed_d add
   constraint text_nonempty_ste1000_trimmed_d_c_trimmed check (is_trimmed(value));

create domain text_nonempty_ste2000_d text_nonempty_inf_d;
alter domain  text_nonempty_ste2000_d add
   constraint text_nonempty_ste2000_d_c_ste2000 check (length(value) <= 2000);

create domain text_nonempty_ste4000_d text_nonempty_inf_d;
alter domain  text_nonempty_ste4000_d add
   constraint text_nonempty_ste4000_d_c_ste4000 check (length(value) <= 4000);

create domain text_nonempty_ste8000_d text_nonempty_inf_d;
alter domain  text_nonempty_ste8000_d add
   constraint text_nonempty_ste8000_d_c_ste8000 check (length(value) <= 8000);

create domain text_nonempty_ste16000_d text_nonempty_inf_d;
alter domain  text_nonempty_ste16000_d add
   constraint text_nonempty_ste16000_d_c_ste16000 check (length(value) <= 16000);


create domain jsonb_ste500_d jsonb;
alter domain  jsonb_ste500_d add
   constraint jsonb_ste500_d_c_ste500 check (pg_column_size(value) <= 500);

create domain jsonb_ste1000_d jsonb;
alter domain  jsonb_ste1000_d add
   constraint jsonb_ste1000_d_c_ste1000 check (pg_column_size(value) <= 1000);

create domain jsonb_ste2000_d jsonb;
alter domain  jsonb_ste2000_d add
   constraint jsonb_ste2000_d_c_ste2000 check (pg_column_size(value) <= 2000);

create domain jsonb_ste4000_d jsonb;
alter domain  jsonb_ste4000_d add
   constraint jsonb_ste4000_d_c_ste4000 check (pg_column_size(value) <= 4000);

create domain jsonb_ste8000_d jsonb;
alter domain  jsonb_ste8000_d add
   constraint jsonb_ste8000_d_c_ste8000 check (pg_column_size(value) <= 8000);

create domain jsonb_ste16000_d jsonb;
alter domain  jsonb_ste16000_d add
   constraint jsonb_ste16000_d_c_ste16000 check (pg_column_size(value) <= 16000);


-- lt_2 -->  lt2  ?   abs_lt2  ->  abslt2 ?
create domain i32_lt2e9_d i32_d;
alter  domain i32_lt2e9_d add
   constraint i32_lt2e9_d_c_lt_2e9 check (value < 2000000000);

create domain i64_lt2e9_d i64_d;
alter  domain i64_lt2e9_d add
   constraint i64_lt2e9_d_c_lt_2e9 check (value < 2000000000);

create domain i32_abs_lt2e9_d i32_lt2e9_d;
alter  domain i32_abs_lt2e9_d add
   constraint i32_abs_lt2e9_d_c_gt_m2e9 check (value > -2000000000);

create domain i64_abs_lt2e9_d i64_lt2e9_d;
alter  domain i64_abs_lt2e9_d add
   constraint i64_abs_lt2e9_d_c_gt_m2e9 check (value > -2000000000);

create domain i32_abs_lt2e9_nz_d i32_abs_lt2e9_d;
alter  domain i32_abs_lt2e9_nz_d add
   constraint i32_abs_lt2e9_nz_d_c_nz check (value <> 0);

create domain i64_abs_lt2e9_nz_d i64_abs_lt2e9_d;
alter  domain i64_abs_lt2e9_nz_d add
   constraint i64_abs_lt2e9_nz_d_c_nz check (value <> 0);

create domain i32_lt2e9_gz_d i32_lt2e9_d;
alter  domain i32_lt2e9_gz_d add
   constraint i32_lt2e9_gz_d_c_gz check (value > 0);

create domain i64_lt2e9_gz_d i64_lt2e9_d;
alter  domain i64_lt2e9_gz_d add
   constraint i64_lt2e9_gz_d_c_gz check (value > 0);

create domain i32_lt2e9_gt1000_d i32_lt2e9_d;
alter  domain i32_lt2e9_gt1000_d add
   constraint i32_lt2e9_gt1000_d_c_gt1000 check (value > 1000);

create domain i64_lt2e9_gt1000_d i64_lt2e9_d;
alter  domain i64_lt2e9_gt1000_d add
   constraint i64_lt2e9_gt1000_d_c_gt1000 check (value > 1000);


create domain page_id_st_d text_nonempty_ste60_d;
alter  domain page_id_st_d add
   constraint page_id_st_d_c_chars check (value ~ '^[a-zA-Z0-9_]*$');

create domain page_id_d__later  i64_lt2e9_gz_d;

create domain site_id_d     i32_abs_lt2e9_nz_d;
create domain cat_id_d      i32_lt2e9_gz_d;
create domain tagtype_id_d  i32_lt2e9_gt1000_d;

create domain pat_id_d      i32_abs_lt2e9_nz_d;

create domain member_id_d   pat_id_d;
alter  domain member_id_d add
   constraint member_id_d_c_gtz check (value > 0);


create domain webhook_id_d   i32_lt2e9_gz_d;
create domain event_id_d     i64_lt2e9_gz_d;
create domain event_type_d   i16_gz_d;  -- for now



-- Webhooks
-------------------------------------------------

-- Hmm, let's enable, if API enabled?
-- alter table settings3 add column enable_webhooks_c bool;


create table webhooks_t (
  site_id_c    site_id_d,     -- pk
  webhook_id_c webhook_id_d,  -- pk

  owner_id_c   pat_id_d not null,
  run_as_id_c  pat_id_d,

  enabled_c    bool not null,
  deleted_c    bool not null,

  descr_c                text_nonempty_ste500_trimmed_d,
  send_to_url_c          http_url_d not null,
  check_dest_cert_c      bool,   -- if should check the TLS cert of the send_to_url_c.
  send_event_types_c     int[], -- event_type_d[],
  send_event_subtypes_c  int[], -- maybe later
  send_format_v_c        i16_gz_d not null,
  send_max_reqs_per_sec_c    f32_gz_d,
  -- see e.g.: https://doc.batch.com/api/webhooks, [10..=1000] 100 default.
  send_max_events_per_req_c  i16_gz_d,   -- default 100? Max 500, Zapier limit
  -- https://community.zapier.com/code-webhooks-52/putting-many-api-objects-into-one-zap-vs-one-zap-per-object-7697
  --  —>  https://zapier.com/help/create/other-functions/loop-your-zap-actions
  --  &  https://community.zapier.com/featured-articles-65/by-zapier-learn-about-looping-11670
  --  &  https://community.zapier.com/featured-articles-65/how-to-repeat-action-s-in-your-zap-for-a-variable-number-of-values-3037
  --         let data = []; ... data[i] = { ... }; output = data;
  -- see e.g.: https://doc.batch.com/api/webhooks, [1..=30], 5s default.
  send_max_delay_secs_c      i16_gz_d,  -- default 10?  [1..3600*24]?
  send_custom_headers_c      jsonb_ste4000_d,

  retry_max_secs_c      i16_gz_d,
  retry_max_times_c     i16_gz_d,

  failed_reason_c       i16_gz_d,
  failed_since_c        timestamp,
  failed_message_c      text_nonempty_ste16000_d,
  retried_num_times_c   i16_gez_d,
  retried_num_mins_c    i16_gez_d,
  broken_reason_c       i16_gz_d,

  sent_up_to_when_c     timestamp,
  sent_up_to_event_id_c event_id_d,
  num_pending_maybe_c   i16_gez_d,
  done_for_now_c        bool,
  -- Not needed, if batching, and one req at a time:
  retry_event_ids_c     int[], -- not?: event_id_d[],

  constraint webhooks_p_id primary key (site_id_c, webhook_id_c),

  -- fk ix: pk
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
create index webhooks_ig_runasid on webhooks_t (send_to_url_c);
create index webhooks_ig_sentuptowhen_c on webhooks_t (sent_up_to_when_c);

-- To consider, the next time.
create index webhooks_ig_sentuptowhen_more_c on webhooks_t (sent_up_to_when_c)
    where done_for_now_c is not true and broken_reason_c is null;



create table webhook_reqs_out_t (
  site_id_c     site_id_d,  -- pk
  webhook_id_c  webhook_id_d,  -- pk
  -- ren to generated_at ?
  sent_at_c     timestamp not null,  -- pk ?

  -- ren: remove "sent_" prefix — maybe never got sent? if e.g. connnection refused.
  sent_to_url_c          http_url_d not null,
  sent_by_app_ver_c      text_nonempty_ste120_d not null,
  sent_format_v_c        i16_gz_d not null,
  sent_event_types_c     int[] not null,  -- event_type_d[],   was rend from: sent_types_c
  sent_event_subtypes_c  int[],
  sent_event_ids_c       int[],  -- event_id_d[],
  sent_json_c            jsonb not null,
  sent_headers_c         jsonb_ste8000_d,

  failed_at_c         timestamp,
  failed_how_c        i16_gz_d,
  failed_msg_c        text_nonempty_ste16000_d,

  resp_at_c           timestamp,
  resp_status_c       i16_gz_d,
  resp_status_text_c  text_nonempty_ste120_trimmed_d,
  resp_body_c         text_nonempty_ste16000_d,
  resp_headers_c      jsonb_ste8000_d,


  constraint webhooksent_p primary key (site_id_c, webhook_id_c, sent_at_c),

  -- fk ix: pk
  constraint webhookreqsout_webhookid_r_webhooks foreign key (site_id_c, webhook_id_c)
    references webhooks_t (site_id_c, webhook_id_c),

  constraint webhookreqsout_c_not_yet_any_subtypes check (
      cardinality(sent_event_subtypes_c) = 0),

  -- Each event is of one main type, e.g. PageCreated or PageUpdated.
  constraint webhookreqsout_c_num_ev_types_lte_num_evs check (
      cardinality(sent_event_types_c) <= cardinality(sent_event_ids_c)),

  constraint webhookreqsout_c_sent_bef_failed check (sent_at_c <= failed_at_c),
  constraint webhookreqsout_c_sent_bef_resp   check (sent_at_c <= resp_at_c),

  constraint webhookreqsout_c_failed_at_how_null check (
      (failed_at_c is null) = (failed_how_c is null)),

  constraint webhookreqsout_c_failed_at_msg_null check (
      (failed_at_c is not null) or (failed_msg_c is null)),

  constraint webhookreqsout_c_resp_at_status_null check (
      (resp_at_c is null) = (resp_status_c is null)),

  constraint webhookreqsout_c_resp_at_statustext_null check (
      (resp_at_c is not null) or (resp_status_text_c is null)),

  constraint webhookreqsout_c_resp_at_body_null check (
      (resp_at_c is not null) or (resp_body_c is null)),

  constraint webhookreqsout_c_resp_at_headers_null check (
      (resp_at_c is not null) or (resp_headers_c is null))
);


-- Skip sent_event_types_c — would probably just be a full scan anyway?
-- But if there'll be 
create index webhookreqsout_i_senteventsubtypes
    on webhook_reqs_out_t using gin ("sent_event_subtypes_c");
create index webhookreqsout_i_senteventids
    on webhook_reqs_out_t using gin ("sent_event_ids_c");

