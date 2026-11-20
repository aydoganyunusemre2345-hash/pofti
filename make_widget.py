import os

R = "android/app/src/main/res"
for d in ("drawable", "layout", "xml"):
    os.makedirs(f"{R}/{d}", exist_ok=True)

BODY = "M22,0 H78 A22,22 0 0 1 100,22 V78 A22,22 0 0 1 78,100 H22 A22,22 0 0 1 0,78 V22 A22,22 0 0 1 22,0 Z"

def eye(cx, dx):
    x = cx + dx
    return f"M{x},30 a7,7 0 0 1 7,7 v12 a7,7 0 0 1 -14,0 v-12 a7,7 0 0 1 7,-7 z"

def fill(path, color):
    return f'<path android:fillColor="{color}" android:pathData="{path}"/>'

def stroke(path, w):
    return f'<path android:strokeColor="#FFFFFF" android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{path}"/>'

MOUTH = "M40,72 h20 a2.5,2.5 0 0 1 0,5 h-20 a2.5,2.5 0 0 1 0,-5 z"
SMOUTH = "M46,74 h8 a2,2 0 0 1 0,4 h-8 a2,2 0 0 1 0,-4 z"

def vec(name, body_color, inner, extra=""):
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="120dp" android:height="120dp" android:viewportWidth="120" android:viewportHeight="120">
<group android:translateX="6" android:translateY="22" android:scaleX="0.88" android:scaleY="0.88">
{fill(BODY, body_color)}
{inner}
</group>
{extra}
</vector>
'''
    open(f"{R}/drawable/{name}.xml", "w", encoding="utf-8").write(xml)

def eyes_open(dx):
    return fill(eye(35, dx), "#FFFFFF") + fill(eye(65, dx), "#FFFFFF")

vec("pw_open", "#3B82F6", eyes_open(0) + fill(MOUTH, "#93C5FD"))
vec("pw_left", "#3B82F6", eyes_open(-5) + fill(MOUTH, "#93C5FD"))
vec("pw_right", "#3B82F6", eyes_open(5) + fill("M40,72 h20 a2.5,2.5 0 0 1 0,5 h-20 a2.5,2.5 0 0 1 0,-5 z", "#93C5FD"))
vec("pw_blink", "#3B82F6", fill("M28,44 h14 v3.5 h-14 z", "#FFFFFF") + fill("M58,44 h14 v3.5 h-14 z", "#FFFFFF") + fill(MOUTH, "#93C5FD"))

sleep_eyes = stroke("M28,46 q7,6 14,0", 3) + stroke("M58,46 q7,6 14,0", 3) + fill(SMOUTH, "#93C5FD")
z1 = stroke("M84,32 h10 l-10,12 h10", 2.5)
z2 = stroke("M94,17 h13 l-13,15 h13", 3)
z3 = stroke("M103,2 h15 l-15,17 h15", 3.5)
vec("pw_z1", "#1E40AF", sleep_eyes, z1)
vec("pw_z2", "#1E40AF", sleep_eyes, z1 + z2)
vec("pw_z3", "#1E40AF", sleep_eyes, z1 + z2 + z3)

def img(n):
    return f'<ImageView android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="fitCenter" android:src="@drawable/{n}"/>'

awake = ["pw_open", "pw_open", "pw_left", "pw_open", "pw_open", "pw_right", "pw_open", "pw_blink"]
sleepf = ["pw_z1", "pw_z2", "pw_z3"]
layout = f'''<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/pw_root" android:layout_width="match_parent" android:layout_height="match_parent" android:padding="4dp">
<ViewFlipper android:id="@+id/pw_awake" android:layout_width="match_parent" android:layout_height="match_parent" android:autoStart="true" android:flipInterval="450">
{"".join(img(n) for n in awake)}
</ViewFlipper>
<ViewFlipper android:id="@+id/pw_sleep" android:layout_width="match_parent" android:layout_height="match_parent" android:autoStart="true" android:flipInterval="900" android:visibility="gone">
{"".join(img(n) for n in sleepf)}
</ViewFlipper>
</FrameLayout>
'''
open(f"{R}/layout/pofti_widget.xml", "w", encoding="utf-8").write(layout)

info = '''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp" android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/pofti_widget"
    android:previewImage="@drawable/pw_open"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
'''
open(f"{R}/xml/pofti_widget_info.xml", "w", encoding="utf-8").write(info)
print("widget kaynakları hazır")
