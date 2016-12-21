package io.mycat.jcache.enums;

/**
 * 二进制协议 状态
 * @author liyanjun
 *
 */
public enum BIN_substates {

    bin_no_state,
    bin_reading_set_header,
    bin_reading_cas_header,
    bin_read_set_value,
    bin_reading_get_key,
    bin_reading_stat,
    bin_reading_del_header,
    bin_reading_incr_header,
    bin_read_flush_exptime,
    bin_reading_sasl_auth,
    bin_reading_sasl_auth_data,
    bin_reading_touch_key
}
