package tv.proxfibre;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Sert les APK fournis (Armadillo, AirPlay), extraits des assets dans le stockage interne,
 * a l'installateur du systeme (content:// au lieu de file://, interdit depuis Android 7).
 */
public class ApkProvider extends ContentProvider {

    static final String AUTHORITY = "tv.proxfibre.apks";

    public static File apkFile(Context c, String name) {
        return new File(c.getFilesDir(), name);
    }

    public static Uri uri(Context c, String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    /** Nom de fichier demande ; refuse tout ce qui n'est pas un .apk du dossier prive. */
    private static File resolve(Context c, Uri u) throws FileNotFoundException {
        String name = u.getLastPathSegment();
        if (name == null || !name.endsWith(".apk") || name.contains("/")) throw new FileNotFoundException();
        File f = apkFile(c, name);
        if (!f.isFile()) throw new FileNotFoundException(name);
        return f;
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(getContext(), u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri u, String[] projection, String selection, String[] args, String sort) {
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        File f = apkFile(getContext(), u.getLastPathSegment());
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            row[i] = OpenableColumns.SIZE.equals(cols[i]) ? (Object) f.length() : (Object) f.getName();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri u) { return "application/vnd.android.package-archive"; }

    @Override
    public Uri insert(Uri u, ContentValues values) { return null; }

    @Override
    public int delete(Uri u, String selection, String[] args) { return 0; }

    @Override
    public int update(Uri u, ContentValues values, String selection, String[] args) { return 0; }
}
