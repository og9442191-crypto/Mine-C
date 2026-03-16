package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.ShortcutSettingsDialog;

import java.util.ArrayList;
import java.util.List;

public class ShortcutsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadShortcutsList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.shortcuts);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.shortcuts_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        return frameLayout;
    }

    public void loadShortcutsList() {
        ArrayList<Shortcut> shortcuts = manager.loadShortcuts();
        recyclerView.setAdapter(new ShortcutsAdapter(shortcuts));
        if (shortcuts.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private class ShortcutsAdapter extends RecyclerView.Adapter<ShortcutsAdapter.ViewHolder> {
        private final List<Shortcut> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final View innerArea;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.subtitle = view.findViewById(R.id.TVSubtitle);
                this.menuButton = view.findViewById(R.id.BTMenu);
                this.innerArea = view.findViewById(R.id.LLInnerArea);
            }
        }

        public ShortcutsAdapter(List<Shortcut> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.shortcut_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Shortcut item = data.get(position);
            if (item.icon != null) holder.imageView.setImageBitmap(item.icon);
            holder.title.setText(item.name);
            holder.subtitle.setText(item.container.getName());
            holder.menuButton.setOnClickListener((v) -> showListItemMenu(v, item));
            holder.innerArea.setOnClickListener((v) -> runFromShortcut(item));
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, final Shortcut shortcut) {
            final Context context = getContext();
            PopupMenu listItemMenu = new PopupMenu(context, anchorView);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.inflate(R.menu.shortcut_popup_menu);
            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.shortcut_settings) {
                    (new ShortcutSettingsDialog(ShortcutsFragment.this, shortcut)).show();
                }
                else if (itemId == R.id.shortcut_remove) {
                    ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                        if (shortcut.file.delete() && shortcut.iconFile != null) shortcut.iconFile.delete();
                        loadShortcutsList();
                    });
                }
                return true;
            });
            listItemMenu.show();
        }

        private void runFromShortcut(Shortcut shortcut) {
            Activity activity = getActivity();

            if (!XrActivity.isSupported()) {
                Intent intent = new Intent(activity, XServerDisplayActivity.class);
                intent.putExtra("container_id", shortcut.container.id);
                intent.putExtra("shortcut_path", shortcut.file.getPath());
                activity.startActivity(intent);
            }
            else XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.shortcuts_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.shortcuts_menu_import) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            ((MainActivity)getActivity()).setOpenFileCallback((uri) -> {
                importAndRunExe(uri);
            });
            startActivityForResult(Intent.createChooser(intent, "Selecionar .exe"), 1001);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void importAndRunExe(Uri uri) {
        try {
            android.content.Context context = getContext();
            java.io.File exeDir = new java.io.File(context.getFilesDir(), "imported_games");
            exeDir.mkdirs();
            String fileName = uri.getLastPathSegment();
            if (fileName != null && fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }
            java.io.File destFile = new java.io.File(exeDir, fileName);
            try (java.io.InputStream in = context.getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            // Pegar o primeiro container disponivel e rodar
            java.util.List<com.winlator.container.Container> containers = manager.getContainers();
            if (!containers.isEmpty()) {
                com.winlator.container.Container container = containers.get(0);
                Activity activity = getActivity();
                if (!com.winlator.xserver.XrActivity.isSupported()) {
                    Intent intent = new Intent(activity, com.winlator.XServerDisplayActivity.class);
                    intent.putExtra("container_id", container.id);
                    intent.putExtra("shortcut_path", destFile.getPath());
                    activity.startActivity(intent);
                } else com.winlator.xserver.XrActivity.openIntent(activity, container.id, destFile.getPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}