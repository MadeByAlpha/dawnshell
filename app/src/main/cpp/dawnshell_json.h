#ifndef DAWNSHELL_JSON_H
#define DAWNSHELL_JSON_H

/*
 * Single-line JSON event writer for DawnShell's native helpers (JSON Lines,
 * schema docs/native-output-schema.md). Write-only: no parser, no allocation.
 * Every line starts with {"v":1,"ts":<epoch>,"event":"<name>"} and is always
 * valid JSON. A field that does not fit is dropped and "truncated":true is set.
 */

#include <stdbool.h>
#include <stddef.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define DS_JSON_SCHEMA_VERSION 1
#define DS_JSON_RESERVE 40

/* Kept out of line: inlining the writer at every call site costs ~16 KiB per ABI. */
#define DS_JSON_OUTLINE static __attribute__((noinline, unused))

#ifndef DS_JSON_NOW
#define DS_JSON_NOW() ((long long) time(NULL))
#endif

typedef struct {
    char *buf;
    size_t cap;
    size_t len;
    int depth;
    bool first;
    bool overflow;
} DsJson;

DS_JSON_OUTLINE void ds_json_put(DsJson *j, const char *text, size_t length) {
    if (j->overflow || j->len + length + DS_JSON_RESERVE > j->cap) {
        j->overflow = true;
        return;
    }
    memcpy(j->buf + j->len, text, length);
    j->len += length;
}

static inline void ds_json_putc(DsJson *j, char c) {
    ds_json_put(j, &c, 1);
}

static inline size_t ds_json_utf8_length(const unsigned char *s, size_t remaining) {
    size_t need = s[0] >= 0xf0 ? 4 : s[0] >= 0xe0 ? 3 : s[0] >= 0xc2 ? 2 : 0;
    if (need == 0 || need > remaining || s[0] > 0xf4) return 0;
    unsigned code = s[0] & (0xffu >> (need + 1));
    for (size_t i = 1; i < need; i++) {
        if ((s[i] & 0xc0) != 0x80) return 0;
        code = (code << 6) | (s[i] & 0x3fu);
    }
    static const unsigned minimum[5] = {0, 0, 0x80, 0x800, 0x10000};
    if (code < minimum[need] || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff)) return 0;
    return need;
}

DS_JSON_OUTLINE void ds_json_quoted(DsJson *j, const char *value) {
    static const char hex[] = "0123456789abcdef";
    ds_json_putc(j, '"');
    const unsigned char *s = (const unsigned char *) (value != NULL ? value : "");
    for (size_t i = 0; s[i] != '\0' && !j->overflow;) {
        unsigned char c = s[i];
        if (c == '"' || c == '\\') {
            const char pair[2] = {'\\', (char) c};
            ds_json_put(j, pair, 2);
        } else if (c == '\n' || c == '\r' || c == '\t') {
            const char pair[2] = {'\\', c == '\n' ? 'n' : c == '\r' ? 'r' : 't'};
            ds_json_put(j, pair, 2);
        } else if (c < 0x20) {
            const char escape[6] = {'\\', 'u', '0', '0', hex[c >> 4], hex[c & 15]};
            ds_json_put(j, escape, 6);
        } else if (c < 0x80) {
            ds_json_putc(j, (char) c);
        } else {
            size_t available = 1;
            while (available < 4 && s[i + available] != '\0') available++;
            size_t length = ds_json_utf8_length(s + i, available);
            if (length == 0) {
                ds_json_putc(j, '?');
            } else {
                ds_json_put(j, (const char *) s + i, length);
                i += length;
                continue;
            }
        }
        i++;
    }
    ds_json_putc(j, '"');
}

/* Starts a field; ds_json_commit drops it again when it overflowed. */
static inline size_t ds_json_key(DsJson *j, const char *key) {
    size_t start = j->len;
    if (!j->first) ds_json_putc(j, ',');
    j->first = false;
    ds_json_quoted(j, key);
    ds_json_putc(j, ':');
    return start;
}

static inline void ds_json_commit(DsJson *j, size_t start) {
    if (j->overflow) j->len = start;
}

DS_JSON_OUTLINE void ds_json_int(DsJson *j, const char *key, long long value) {
    size_t start = ds_json_key(j, key);
    char text[24];
    size_t pos = sizeof(text);
    unsigned long long magnitude = value < 0 ? 0ull - (unsigned long long) value : (unsigned long long) value;
    do {
        text[--pos] = (char) ('0' + magnitude % 10);
        magnitude /= 10;
    } while (magnitude != 0);
    if (value < 0) text[--pos] = '-';
    ds_json_put(j, text + pos, sizeof(text) - pos);
    ds_json_commit(j, start);
}

DS_JSON_OUTLINE void ds_json_bool(DsJson *j, const char *key, bool value) {
    size_t start = ds_json_key(j, key);
    ds_json_put(j, value ? "true" : "false", value ? 4 : 5);
    ds_json_commit(j, start);
}

DS_JSON_OUTLINE void ds_json_str(DsJson *j, const char *key, const char *value) {
    size_t start = ds_json_key(j, key);
    ds_json_quoted(j, value);
    ds_json_commit(j, start);
}

DS_JSON_OUTLINE void ds_json_obj_begin(DsJson *j, const char *key) {
    size_t start = ds_json_key(j, key);
    ds_json_putc(j, '{');
    ds_json_commit(j, start);
    if (!j->overflow) j->depth = 1;
    j->first = true;
}

DS_JSON_OUTLINE void ds_json_obj_end(DsJson *j) {
    if (j->depth > 0) ds_json_putc(j, '}');
    if (!j->overflow) j->depth = 0;
    j->first = false;
}

DS_JSON_OUTLINE void ds_json_begin(DsJson *j, char *buf, size_t cap, const char *event) {
    *j = (DsJson) {.buf = buf, .cap = cap, .first = true};
    ds_json_putc(j, '{');
    ds_json_int(j, "v", DS_JSON_SCHEMA_VERSION);
    ds_json_int(j, "ts", DS_JSON_NOW());
    ds_json_str(j, "event", event);
}

/* Closes the object, appends the newline and returns the line length. */
static inline size_t ds_json_finish(DsJson *j) {
    if (j->depth > 0) {
        j->buf[j->len++] = '}';
        j->depth = 0;
    }
    if (j->overflow) {
        memcpy(j->buf + j->len, ",\"truncated\":true", 17);
        j->len += 17;
    }
    j->buf[j->len++] = '}';
    j->buf[j->len++] = '\n';
    return j->len;
}

/* One write(2) per line keeps lines intact on pipes up to PIPE_BUF. */
DS_JSON_OUTLINE void ds_json_emit(DsJson *j, int fd) {
    size_t total = ds_json_finish(j);
    for (size_t done = 0; done < total;) {
        ssize_t count = write(fd, j->buf + done, total - done);
        if (count <= 0) return;
        done += (size_t) count;
    }
}

#endif
