"""Generate the shared Windows/Android merge contract cases. No personal data."""
import copy
import json
from pathlib import Path

def item(n, **changes):
    value = dict(id=f"{n:032x}", title=f"演示待办 {n}", notes="中文 / emoji 🌿\n第二行", due=1790000000123, created=1789990000123, done=False, lead=60, deleted=False)
    value.update(changes)
    return value

def dead(n):
    return item(n, title="", notes="", due=0, created=0, lead=0, deleted=True)

def doc(items):
    return dict(schema=1, items=items)

cases=[]
def case(name, baseline, local, remote, expected=None, outcome="ok"):
    cases.append(dict(name=name, baseline=doc(baseline), local=doc(local), remote=doc(remote), expected=doc(expected or []), outcome=outcome))

a,b=item(1),item(2)
case("first-merge",[],[a],[b],[a,b])
case("pull-edit",[a],[a],[item(1,title="手机修改")],[item(1,title="手机修改")])
case("push-completion",[a],[item(1,done=True)],[a],[item(1,done=True)])
case("independent-edits",[a,b],[item(1,title="电脑修改"),b],[a,item(2,done=True)],[item(1,title="电脑修改"),item(2,done=True)])
case("same-edit",[a],[item(1,done=True)],[item(1,done=True)],[item(1,done=True)])
case("concurrent-edit",[a],[item(1,title="本机")],[item(1,title="云端")],outcome="conflict")
case("delete-versus-edit",[a],[dead(1)],[item(1,title="修改")],outcome="conflict")
case("remote-delete",[a],[a],[dead(1)],[dead(1)])
case("offline-delete",[a],[dead(1)],[a],[dead(1)])
case("first-sync-versus-tombstone",[],[a],[dead(1)],outcome="conflict")
case("remote-reset-rejected",[a],[a],[],outcome="error")
case("deleted-stays-deleted",[dead(1)],[dead(1)],[dead(1)],[dead(1)])
target=Path(__file__).resolve().parent.parent / "android/app/src/androidTest/assets/cases.json"
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(json.dumps(dict(cases=cases),ensure_ascii=False,indent=2),encoding="utf-8")
