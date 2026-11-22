#define DS_JSON_NOW() 1700000000LL
#include "dawnshell_json.h"

#include <stdio.h>

static int failures;

static void expect(const char *name, DsJson *j, const char *expected) {
    ds_json_finish(j);
    j->buf[j->len] = '\0';
    if (strcmp(j->buf, expected) != 0) {
        fprintf(stderr, "FAIL %s\n  got:      %s  expected: %s", name, j->buf, expected);
        failures++;
    }
}

int main(void) {
    char buf[256];
    DsJson j;

    ds_json_begin(&j, buf, sizeof(buf), "running");
    ds_json_int(&j, "pid", -42);
    ds_json_bool(&j, "ok", true);
    ds_json_obj_begin(&j, "ns");
    ds_json_int(&j, "pid", 4026531836LL);
    ds_json_int(&j, "net", 0);
    ds_json_obj_end(&j);
    ds_json_str(&j, "mode", "systemd");
    expect("fields", &j,
           "{\"v\":1,\"ts\":1700000000,\"event\":\"running\",\"pid\":-42,\"ok\":true,"
           "\"ns\":{\"pid\":4026531836,\"net\":0},\"mode\":\"systemd\"}\n");

    ds_json_begin(&j, buf, sizeof(buf), "failed");
    ds_json_str(&j, "error", "quote\" back\\ nl\n tab\t ctl\x01 \xec\x95\x88 bad\xff end");
    expect("escape", &j,
           "{\"v\":1,\"ts\":1700000000,\"event\":\"failed\",\"error\":"
           "\"quote\\\" back\\\\ nl\\n tab\\t ctl\\u0001 \xec\x95\x88 bad? end\"}\n");

    ds_json_begin(&j, buf, sizeof(buf), "x");
    ds_json_int(&j, "min", (-9223372036854775807LL - 1));
    expect("int_min", &j,
           "{\"v\":1,\"ts\":1700000000,\"event\":\"x\",\"min\":-9223372036854775808}\n");

    char small[96];
    ds_json_begin(&j, small, sizeof(small), "diagnostic");
    ds_json_str(&j, "content", "this value is far too long to fit in the tiny buffer used here");
    ds_json_int(&j, "after", 1);
    expect("truncated", &j,
           "{\"v\":1,\"ts\":1700000000,\"event\":\"diagnostic\",\"truncated\":true}\n");

    ds_json_begin(&j, small, sizeof(small), "health");
    ds_json_obj_begin(&j, "ns");
    ds_json_str(&j, "a", "0123456789012345678901234567890123456789012345678901234567890123456789");
    expect("truncated_nested", &j,
           "{\"v\":1,\"ts\":1700000000,\"event\":\"health\",\"ns\":{},\"truncated\":true}\n");

    if (failures == 0) puts("dawnshell_json: all tests passed");
    return failures == 0 ? 0 : 1;
}
