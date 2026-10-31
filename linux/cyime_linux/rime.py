"""Small binding to librime's versioned public C API, not a new Pinyin decoder."""

import ctypes as C
import ctypes.util
import os
from pathlib import Path


class Traits(C.Structure):
    _fields_ = [("data_size", C.c_int)] + [(name, C.c_char_p) for name in (
        "shared_data_dir", "user_data_dir", "distribution_name", "distribution_code_name",
        "distribution_version", "app_name")] + [("modules", C.c_void_p), ("min_log_level", C.c_int)] + [
        (name, C.c_char_p) for name in ("log_dir", "prebuilt_data_dir", "staging_dir")]


class Composition(C.Structure):
    _fields_ = [(name, C.c_int) for name in ("length", "cursor_pos", "sel_start", "sel_end")] + [("preedit", C.c_char_p)]


class Candidate(C.Structure):
    _fields_ = [("text", C.c_char_p), ("comment", C.c_char_p), ("reserved", C.c_void_p)]


class Menu(C.Structure):
    _fields_ = [(name, C.c_int) for name in ("page_size", "page_no", "is_last_page",
        "highlighted_candidate_index", "num_candidates")] + [("candidates", C.POINTER(Candidate)), ("select_keys", C.c_char_p)]


class Context(C.Structure):
    _fields_ = [("data_size", C.c_int), ("composition", Composition), ("menu", Menu),
                ("commit_text_preview", C.c_char_p), ("select_labels", C.POINTER(C.c_char_p))]


class Commit(C.Structure):
    _fields_ = [("data_size", C.c_int), ("text", C.c_char_p)]


# ABI entries through select_schema are stable since librime 1.0. Check data_size
# before touching a function, so a missing/older library fails explicitly.
API_NAMES = """setup set_notification_handler initialize finalize start_maintenance
is_maintenance_mode join_maintenance_thread deployer_initialize prebuild deploy
deploy_schema deploy_config_file sync_user_data create_session find_session
destroy_session cleanup_stale_sessions cleanup_all_sessions process_key
commit_composition clear_composition get_commit free_commit get_context free_context
get_status free_status set_option get_option set_property get_property get_schema_list
free_schema_list get_current_schema select_schema""".split()
API_NAMES += """schema_open config_open config_close config_get_bool config_get_int config_get_double
config_get_string config_get_cstring config_update_signature config_begin_map config_next config_end
simulate_key_sequence register_module find_module run_task get_shared_data_dir get_user_data_dir
get_sync_dir get_user_id get_user_data_sync_dir config_init config_load_string config_set_bool
config_set_int config_set_double config_set_string config_get_item config_set_item config_clear
config_create_list config_create_map config_list_size config_begin_list get_input get_caret_pos
select_candidate get_version set_caret_pos select_candidate_on_current_page candidate_list_begin
candidate_list_next candidate_list_end user_config_open candidate_list_from_index""".split()


class CandidateIterator(C.Structure):
    _fields_ = [("ptr", C.c_void_p), ("index", C.c_int), ("candidate", Candidate)]


class Api(C.Structure):
    _fields_ = [("data_size", C.c_int)] + [(name, C.c_void_p) for name in API_NAMES]


def sized(kind):
    value = kind()
    value.data_size = C.sizeof(kind) - C.sizeof(C.c_int)
    return value


def decode(value):
    return value.decode("utf-8", "replace") if value else ""


class Rime:
    def __init__(self, shared, user, library=None, schema="cyime_pinyin"):
        path = library or os.environ.get("CYIME_RIME_LIBRARY") or C.util.find_library("rime")
        if not path:
            raise RuntimeError("Install librime1 or set CYIME_RIME_LIBRARY")
        self.library = C.CDLL(str(path))
        self.library.rime_get_api.restype = C.POINTER(Api)
        self.api = self.library.rime_get_api().contents
        self.functions = {}
        self.traits = sized(Traits)
        user = Path(user)
        user.mkdir(parents=True, exist_ok=True, mode=0o700)
        for name, value in dict(shared_data_dir=str(shared), user_data_dir=str(user),
                distribution_name="CyIME Linux", distribution_code_name="cyime-linux",
                distribution_version="0.1", app_name="rime.cyime", log_dir=str(user)).items():
            setattr(self.traits, name, value.encode())
        self.traits.min_log_level = 2
        self.fn("setup", None, C.POINTER(Traits))(C.byref(self.traits))
        self.fn("initialize", None, C.POINTER(Traits))(C.byref(self.traits))
        if self.fn("start_maintenance", C.c_int, C.c_int)(0):
            self.fn("join_maintenance_thread", None)()
        self.schema = schema

    def fn(self, name, result, *arguments):
        if name not in self.functions:
            offset = getattr(Api, name).offset
            if self.api.data_size + C.sizeof(C.c_int) < offset + C.sizeof(C.c_void_p) or not getattr(self.api, name):
                raise RuntimeError(f"librime API unavailable: {name}")
            self.functions[name] = C.CFUNCTYPE(result, *arguments)(getattr(self.api, name))
        return self.functions[name]

    def candidates(self, session, start, count):
        iterator = CandidateIterator()
        if not self.fn("candidate_list_from_index", C.c_int, C.c_size_t, C.POINTER(CandidateIterator), C.c_int)(
                session, C.byref(iterator), start):
            return []
        result = []
        try:
            while len(result) < count and self.fn("candidate_list_next", C.c_int, C.POINTER(CandidateIterator))(C.byref(iterator)):
                result.append((decode(iterator.candidate.text), decode(iterator.candidate.comment)))
        finally:
            self.fn("candidate_list_end", None, C.POINTER(CandidateIterator))(C.byref(iterator))
        return result

    def select(self, session, index):
        return bool(self.fn("select_candidate", C.c_int, C.c_size_t, C.c_size_t)(session, index))

    def options(self, session, settings):
        for name, value in (("ascii_punct", settings.ascii_punct), ("full_shape", settings.full_shape)):
            self.set_option(session, name, value)

    def set_option(self, session, name, value):
        self.fn("set_option", None, C.c_size_t, C.c_char_p, C.c_int)(session, name.encode(), int(value))

    def get_option(self, session, name):
        return bool(self.fn("get_option", C.c_int, C.c_size_t, C.c_char_p)(session, name.encode()))

    def session(self):
        session = self.fn("create_session", C.c_size_t)()
        if not session or not self.fn("select_schema", C.c_int, C.c_size_t, C.c_char_p)(session, self.schema.encode()):
            if session:
                self.destroy(session)
            raise RuntimeError(f"Rime schema not deployed: {self.schema}")
        return session

    def key(self, session, key, mask=0):
        return bool(self.fn("process_key", C.c_int, C.c_size_t, C.c_int, C.c_int)(session, key, mask))

    def input(self, session):
        return decode(self.fn("get_input", C.c_char_p, C.c_size_t)(session))

    def commit(self, session):
        value = sized(Commit)
        if not self.fn("get_commit", C.c_int, C.c_size_t, C.POINTER(Commit))(session, C.byref(value)):
            return ""
        try:
            return decode(value.text)
        finally:
            self.fn("free_commit", C.c_int, C.POINTER(Commit))(C.byref(value))

    def context(self, session):
        value = sized(Context)
        if not self.fn("get_context", C.c_int, C.c_size_t, C.POINTER(Context))(session, C.byref(value)):
            return dict(preedit="", preview="", cursor=0, candidates=[], selected=0, last=True, page=0,
                        page_size=6, keys="1234567890")
        try:
            preedit = value.composition.preedit or b""
            return dict(preedit=decode(preedit), preview=decode(value.commit_text_preview),
                confirmed=decode(preedit[:value.composition.sel_start]),
                suffix=decode(preedit[value.composition.sel_end:]),
                cursor=len(preedit[:value.composition.cursor_pos].decode("utf-8", "ignore")),
                candidates=[(decode(value.menu.candidates[i].text), decode(value.menu.candidates[i].comment))
                            for i in range(value.menu.num_candidates)],
                selected=value.menu.highlighted_candidate_index, last=bool(value.menu.is_last_page),
                page=value.menu.page_no, page_size=value.menu.page_size,
                keys=decode(value.menu.select_keys) or "1234567890")
        finally:
            self.fn("free_context", C.c_int, C.POINTER(Context))(C.byref(value))

    def clear(self, session):
        self.fn("clear_composition", None, C.c_size_t)(session)

    def destroy(self, session):
        self.fn("destroy_session", C.c_int, C.c_size_t)(session)

    def close(self):
        self.fn("finalize", None)()
