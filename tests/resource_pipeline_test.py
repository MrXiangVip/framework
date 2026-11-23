#!/usr/bin/env python3
"""Run after compiling framework: python3 tests/resource_pipeline_test.py."""
from pathlib import Path
import subprocess
import tempfile
import zipfile
import runpy
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


def png(pixel):
    def chunk(kind, data):
        return (struct.pack('!I', len(data)) + kind + data
                + struct.pack('!I', zlib.crc32(kind + data)))
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('!2I5B', 2, 1, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b'\0' + bytes(pixel) * 2))
            + chunk(b'IEND', b''))


with tempfile.TemporaryDirectory(prefix='framework-resources-') as directory:
    work = Path(directory)
    apks = []
    for name in ('first', 'second'):
        app = work / name
        (app / 'res/layout').mkdir(parents=True)
        (app / 'res/drawable').mkdir()
        (app / 'res/values').mkdir()
        (app / 'res/values/strings.xml').write_text(
            '<resources><string name="greeting">你好 %s &amp; 世界</string>'
            '<string name="format">你好 %%1$s：%%2$d</string>'
            '<string name="empty"></string>'
            '<string name="escaped">  a\\n\\t\\\\b  </string>'
            '<string name="special">#!=:|</string></resources>' % name, encoding='utf-8')
        icon = app / 'res/drawable/icon.png'
        icon.write_bytes(png((255, 0, 0, 255) if name == 'first' else (0, 0, 255, 255)))
        (app / 'res/drawable/broken.png').write_bytes(b'not an image')
        (app / 'res/drawable/missing.png').write_bytes(icon.read_bytes())
        (app / 'AndroidManifest.xml').write_text('<manifest package="test.%s"/>' % name)
        (app / 'res/layout/main.xml').write_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:id="@+id/title" android:text="%s" />' % name)
        (app / 'res/layout/nested.xml').write_text(
            '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:id="@+id/container"><LinearLayout>'
            '<TextView android:id="@id/title" android:text="nested"/>'
            '</LinearLayout></LinearLayout>')
        (app / 'res/layout/picture.xml').write_text(
            '<ImageView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:src="@drawable/icon"/>')
        (app / 'res/layout/words.xml').write_text(
            '<Button xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:text="@string/greeting"/>')
        output = work / (name + '-build')
        run('python3', ROOT / 'base/tools/generate_resources.py', '--app', app, '--output', output)
        run('javac', '-d', output / 'classes', output / ('generated/test/%s/R.java' % name))
        apk = work / (name + '.apk')
        with zipfile.ZipFile(str(apk), 'w') as archive:
            for base in (output / 'classes', app):
                for path in sorted(base.rglob('*')):
                    if path.is_file() and path.name != 'missing.png':
                        archive.write(str(path), str(path.relative_to(base)))
        apks.append(apk)
        generated = output / ('generated/test/%s/R.java' % name)
        original = generated.read_bytes()
        generate = runpy.run_path(str(ROOT / 'base/tools/generate_resources.py'))['generate']
        generate(app, output)
        assert generated.read_bytes() == original
        duplicate = app / 'res/drawable/icon.jpg'
        duplicate.write_bytes(icon.read_bytes())
        try:
            generate(app, output)
            raise AssertionError('Accepted duplicate drawable name')
        except ValueError:
            pass
        finally:
            duplicate.unlink()
        for invalid in ('@id/missing', '@+id/class', '@+id/bad-name', '@string/missing'):
            bad = app / 'res/layout/bad.xml'
            bad.write_text('<View xmlns:android="http://schemas.android.com/apk/res/android" '
                           'android:text="%s"/>' % invalid)
            try:
                generate(app, output)
                raise AssertionError('Accepted invalid ID: ' + invalid)
            except ValueError:
                pass
            finally:
                bad.unlink()
        for invalid in ('<string name="empty">duplicate</string>',
                        '<string name="class">bad name</string>',
                        '<string name="styled"><b>bold</b></string>',
                        '<string name="alias">@string/greeting</string>'):
            bad = app / 'res/values/bad.xml'
            bad.write_text('<resources>' + invalid + '</resources>')
            try:
                generate(app, output)
                raise AssertionError('Accepted invalid string: ' + invalid)
            except ValueError:
                pass
            finally:
                bad.unlink()
    source = work / 'ResourcePipelineTest.java'
    source.write_text('''
import java.net.*;
import java.io.File;
import com.android.res.Resources;
import com.android.content.ContextWrapper;
import com.android.view.LayoutInflater;
import com.android.app.Activity;
import com.android.widget.TextView;
import com.android.widget.ImageView;
import com.android.view.View;
import org.xmlpull.v1.XmlPullParser;

public class ResourcePipelineTest {
    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            final String name = i == 0 ? "first" : "second";
            try (final URLClassLoader loader = new URLClassLoader(
                    new URL[]{new File(args[i]).toURI().toURL()}, ResourcePipelineTest.class.getClassLoader())) {
                final Resources resources = new Resources(loader);
                int id = loader.loadClass("test." + name + ".R$layout").getField("main").getInt(null);
                ContextWrapper context = new ContextWrapper(null) {
                    public ClassLoader getClassLoader() { return loader; }
                    public Resources getResources() { return resources; }
                };
                check(resources.getLayoutPath(id).equals("res/layout/main.xml"));
                Class<?> strings = loader.loadClass("test." + name + ".R$string");
                int greeting = strings.getField("greeting").getInt(null);
                int format = strings.getField("format").getInt(null);
                check(context.getString(greeting).equals("你好 " + name + " & 世界"));
                check(resources.getStringId("greeting") == greeting);
                check(resources.getStringId("absent") == 0);
                check(context.getString(format, "用户", 3).equals("你好 用户：3"));
                check(context.getString(strings.getField("empty").getInt(null)).isEmpty());
                check(context.getString(strings.getField("special").getInt(null)).equals("#!=:|"));
                check(context.getString(strings.getField("escaped").getInt(null)).equals("  a\\n\\t\\\\b  "));
                TextView words = (TextView) LayoutInflater.from(context).inflate("res/layout/words.xml", null, false);
                check(words.getText().equals(context.getString(greeting)));
                words.setText(strings.getField("empty").getInt(null));
                check(words.getText().isEmpty());
                words.setText(greeting);
                check(words.getText().equals(context.getString(greeting)));
                for (int invalidString : new int[]{0, id, -1, greeting + 99}) {
                    try { context.getString(invalidString); throw new AssertionError(); }
                    catch (Resources.NotFoundException expected) {}
                }
                Class<?> drawables = loader.loadClass("test." + name + ".R$drawable");
                int icon = drawables.getField("icon").getInt(null);
                int expectedPixel = i == 0 ? 0xffff0000 : 0xff0000ff;
                check(resources.getDrawableId("icon") == icon);
                check(resources.getDrawableId("absent") == 0);
                check(resources.getDrawablePath(icon).equals("res/drawable/icon.png"));
                check(resources.getDrawable(icon).getRGB(0, 0) == expectedPixel);
                ImageView picture = new ImageView(context);
                picture.setImageResource(icon);
                check(picture.getImageBitmap().getWidth() == 2);
                check(picture.getImageBitmap().getRGB(0, 0) == expectedPixel);
                check(picture.getImageResource().equals("res/drawable/icon.png"));
                picture.setImageResource(0);
                check(picture.getImageBitmap() == null && picture.getImageResource() == null);
                for (String path : new String[]{"res/drawable/icon.png", "@drawable/icon"}) {
                    picture.setImageResource(path);
                    check(picture.getImageBitmap().getRGB(0, 0) == expectedPixel);
                }
                ImageView inflated = (ImageView) LayoutInflater.from(context).inflate(
                    "res/layout/picture.xml", null, false);
                check(inflated.getImageBitmap().getRGB(0, 0) == expectedPixel);
                for (int badDrawable : new int[]{0, id, -1, icon + 99,
                        drawables.getField("broken").getInt(null), drawables.getField("missing").getInt(null)}) {
                    try { resources.getDrawable(badDrawable); throw new AssertionError(); }
                    catch (Resources.NotFoundException expected) {}
                }
                try { picture.setImageResource(id); throw new AssertionError(); }
                catch (Resources.NotFoundException expected) {}
                check(picture.getImageBitmap().getRGB(0, 0) == expectedPixel);
                TextView view = (TextView) LayoutInflater.from(context).inflate(id, null, false);
                check(view.getText().equals(name));
                Class<?> ids = loader.loadClass("test." + name + ".R$id");
                int title = ids.getField("title").getInt(null);
                int container = ids.getField("container").getInt(null);
                check(view.getResourceId() == title);
                check(view.findViewById(title) == view);
                check(view.findViewById("title") == view);
                View tree = LayoutInflater.from(context).inflate(
                    resources.getLayoutPath(id).replace("main.xml", "nested.xml"), null, false);
                check(tree.findViewById(container) == tree);
                View nested = tree.findViewById(title);
                check(nested instanceof TextView && ((TextView) nested).getText().equals("nested"));
                check(tree.findViewById("@id/title") == nested);
                check(tree.findViewById(View.NO_ID) == null);
                check(tree.findViewById(1234) == null);
                nested.setId(1234);
                check(tree.findViewById(title) == null && tree.findViewById(1234) == nested);
                check(nested.getId() == null);
                nested.setId("@id/title");
                check(tree.findViewById(title) == nested);
                nested.setId("legacy");
                check(nested.getResourceId() == View.NO_ID && tree.findViewById("legacy") == nested);
                nested.setId(title);
                check(tree.findViewById("title") == nested);
                XmlPullParser parser = resources.getLayout(id);
                while (parser.next() != XmlPullParser.START_TAG) {}
                check(parser.getAttributeValue(null, "missing") == null);
                check(name.equals(parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android", "text")));
                final String[] received = new String[1];
                Activity activity = new Activity() {
                    public Resources getResources() { return resources; }
                    public void setContentView(String path) { received[0] = path; }
                };
                activity.setContentView(id);
                check("res/layout/main.xml".equals(received[0]));
                for (int invalid : new int[]{0, title, id + 99}) {
                    try { resources.getLayoutPath(invalid); throw new AssertionError(); }
                    catch (Resources.NotFoundException expected) {}
                }
            }
        }
        System.out.println("Resource pipeline tests passed");
    }
}
''')
    classpath = str(ROOT / 'out/framework/classes') + ':' + str(ROOT / 'libs/*')
    run('javac', '-encoding', 'UTF-8', '-cp', classpath, '-d', work, source)
    run('java', '-Djava.awt.headless=true', '-cp', str(work) + ':' + classpath,
        'ResourcePipelineTest', *apks)
