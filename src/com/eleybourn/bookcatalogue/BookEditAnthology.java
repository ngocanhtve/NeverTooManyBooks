/*
 * @copyright 2013 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter.AnthologyTitleExistsException;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.wikipedia.SearchWikipediaEntryHandler;
import com.eleybourn.bookcatalogue.searches.wikipedia.SearchWikipediaHandler;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class BookEditAnthology extends BookEditFragmentAbstract {

    private static final int DELETE_ID = Menu.FIRST;
    private static final int POPULATE = Menu.FIRST + 1;

    // Trim extraneous punctuation and whitespace from the titles and authors
    private static final String CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";

    private EditText mTitleText;
    private AutoCompleteTextView mAuthorText;
    private String mBookAuthor;
    private String mBookTitle;
    private Button mAdd;
    private CheckBox mSame;
    private Integer mEditPosition = null;
    private int anthology_num = DatabaseHelper.ANTHOLOGY_NO;
    private ArrayList<AnthologyTitle> mList;
    private AnthologyTitleListAdapter mAdapter;

    /**
     * Handle drop events; also preserves current position.
     * TODO: I've been quick&dirty... copied from EditObjectListActivity.. should be unified ?
     */
    private final TouchListView.DropListener mDropListener = new TouchListView.DropListener() {

        @Override
        public void drop(int from, final int to) {
            final ListView lv = getListView();
            // Check if nothing to do; also avoids the nasty case where list size == 1
            if (from == to)
                return;

            final int firstPos = lv.getFirstVisiblePosition();

            AnthologyTitle item = mAdapter.getItem(from);
            mAdapter.remove(item);
            mAdapter.insert(item, to);
            onListChanged();

            int first2 = lv.getFirstVisiblePosition();
            if (BuildConfig.DEBUG) {
                System.out.println(from + " -> " + to + ", first " + firstPos + "(" + first2 + ")");
            }
            final int newFirst = (to > from && from < firstPos) ? (firstPos - 1) : firstPos;

            View firstView = lv.getChildAt(0);
            final int offset = firstView.getTop();
            lv.post(new Runnable() {
                @Override
                public void run() {
                    if (BuildConfig.DEBUG) {
                        System.out.println("Positioning to " + newFirst + "+{" + offset + "}");
                    }
                    lv.requestFocusFromTouch();
                    lv.setSelectionFromTop(newFirst, offset);
                    lv.post(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; ; i++) {
                                View c = lv.getChildAt(i);
                                if (c == null)
                                    break;
                                if (lv.getPositionForView(c) == to) {
                                    lv.setSelectionFromTop(to, c.getTop());
                                    //c.requestFocusFromTouch();
                                    break;
                                }
                            }
                        }
                    });
                }
            });
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_anthology, container, false);
    }

    /**
     * Display the edit fields page
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadPage();
    }

    /**
     * Display the main manage anthology page. This has three parts.
     * 1. Setup the "Same Author" checkbox
     * 2. Setup the "Add Title" fields
     * 3. Populate the "Title List" - {@link #fillAnthology};
     */
    private void loadPage() {

        BookData book = mEditManager.getBookData();
        mBookAuthor = book.getString(ColumnNames.KEY_AUTHOR_FORMATTED);
        mBookTitle = book.getString(ColumnNames.KEY_TITLE);

        // Setup the same author field
        anthology_num = book.getInt(ColumnNames.KEY_ANTHOLOGY_MASK);
        mSame = getView().findViewById(R.id.same_author);
        if ((anthology_num & DatabaseHelper.ANTHOLOGY_MULTIPLE_AUTHORS) != 0) {
            mSame.setChecked(false);
        } else {
            mSame.setChecked(true);
        }

        mSame.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveState(mEditManager.getBookData());
                loadPage();
            }
        });

        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mDb.getAllAuthors());
        mAuthorText = getView().findViewById(R.id.add_author);
        mAuthorText.setAdapter(author_adapter);
        if (mSame.isChecked()) {
            mAuthorText.setVisibility(View.GONE);
        } else {
            mAuthorText.setVisibility(View.VISIBLE);
        }
        mTitleText = getView().findViewById(R.id.add_title);

        mAdd = getView().findViewById(R.id.row_add);
        mAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    String title = mTitleText.getText().toString();
                    String author = mAuthorText.getText().toString();
                    if (mSame.isChecked()) {
                        author = mBookAuthor;
                    }
                    AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) BookEditAnthology.this.getListView().getAdapter());
                    if (mEditPosition == null) {
                        AnthologyTitle anthology = new AnthologyTitle(new Author(author), title);
                        adapter.add(anthology);
                    } else {
                        AnthologyTitle anthology = adapter.getItem(mEditPosition);
                        anthology.setAuthor(new Author(author));
                        anthology.setTitle(title);
                        mEditPosition = null;
                        mAdd.setText(R.string.anthology_add);
                    }
                    mTitleText.setText("");
                    mAuthorText.setText("");
                    //fillAnthology(currentPosition);
                    mEditManager.setDirty(true);
                } catch (AnthologyTitleExistsException e) {
                    Toast.makeText(getActivity(), R.string.the_title_already_exists, Toast.LENGTH_LONG).show();
                }
            }
        });

        fillAnthology();

        TouchListView tlv = (TouchListView) getListView();
        tlv.setDropListener(mDropListener);
    }

    public void fillAnthology(int scroll_to_id) {
        fillAnthology();
        gotoTitle(scroll_to_id);
    }

    /**
     * Populate the bookEditAnthology view
     */
    private void fillAnthology() {

        // Get all of the rows from the database and create the item list
        mList = mEditManager.getBookData().getAnthologyTitles();
        // Now create a simple cursor adapter and set it to display
        mAdapter = new AnthologyTitleListAdapter(getActivity(), R.layout.row_edit_anthology, mList);
        final ListView list = getListView();
        list.setAdapter(mAdapter);
        registerForContextMenu(list);
        list.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mEditPosition = position;
                AnthologyTitle anthology = mList.get(position);
                mTitleText.setText(anthology.getTitle());
                mAuthorText.setText(anthology.getAuthor().getDisplayName());
                mAdd.setText(R.string.anthology_save);
            }
        });
    }

    private ListView getListView() {
        return (ListView) getView().findViewById(android.R.id.list);
    }

    /**
     * Scroll to the current group
     */
    private void gotoTitle(int id) {
        try {
            ListView view = this.getListView();
            view.setSelection(id);
        } catch (Exception e) {
            Logger.logError(e);
        }
        return;
    }

    /**
     * FIXME: android.os.NetworkOnMainThreadException, use a task...
     */
    private void searchWikipedia() {
        String basepath = BookCataloguePreferences.WEBSITE_URL_EN_WIKIPEDIA_ORG;
        String pathAuthor = mBookAuthor.replace(" ", "+");
        pathAuthor = pathAuthor.replace(",", "");
        // Strip everything past the , from the title
        String pathTitle = mBookTitle;
        int comma = pathTitle.indexOf(",");
        if (comma > 0) {
            pathTitle = pathTitle.substring(0, comma);
        }
        pathTitle = pathTitle.replace(" ", "+");
        String path = basepath + "/w/index.php?title=Special:Search&search=%22" + pathTitle + "%22+" + pathAuthor + "";
        boolean success = false;
        URL url;

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;
        SearchWikipediaHandler handler = new SearchWikipediaHandler();
        SearchWikipediaEntryHandler entryHandler = new SearchWikipediaEntryHandler();

        try {
            url = new URL(path);
            parser = factory.newSAXParser();
            try {
                parser.parse(Utils.getInputStream(url), handler);
            } catch (RuntimeException e) {
                Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
                Logger.logError(e);
                return;
            }
            String[] links = handler.getLinks();
            for (String link : links) {
                if (link.isEmpty() || success) {
                    break;
                }
                url = new URL(basepath + link);

                if (BuildConfig.DEBUG) {
                    System.out.println("BEA: Calling out: " + url.toString());
                }

                parser = factory.newSAXParser();
                try {
                    parser.parse(Utils.getInputStream(url), entryHandler);
                    ArrayList<String> titles = entryHandler.getList();
                    /* Display the confirm dialog */
                    if (titles.size() > 0) {
                        success = true;
                        showAnthologyConfirm(titles);
                    }
                } catch (RuntimeException e) {
                    Logger.logError(e);
                    Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
                }
            }
            if (!success) {
                Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
            Logger.logError(e);
        }
        fillAnthology();
        return;
    }

    private void showAnthologyConfirm(final ArrayList<String> titles) {
        StringBuilder anthology_title = new StringBuilder();
        for (int j = 0; j < titles.size(); j++) {
            anthology_title.append("* ").append(titles.get(j)).append("\n");
        }

        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setMessage(anthology_title).create();
        alertDialog.setTitle(R.string.anthology_confirm);
        alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                this.getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        for (int j = 0; j < titles.size(); j++) {
                            String anthology_title = titles.get(j);
                            anthology_title = anthology_title + ", ";
                            String anthology_author = mBookAuthor;
                            // Does the string look like "Hindsight by Jack Williamson"
                            int pos = anthology_title.indexOf(" by ");
                            if (pos > 0) {
                                anthology_author = anthology_title.substring(pos + 4);
                                anthology_title = anthology_title.substring(0, pos);
                            }
                            // Trim extraneous punctuation and whitespace from the titles and authors
                            anthology_author = anthology_author.trim()
                                    .replace("\n", " ")
                                    .replaceAll(CLEANUP_REGEX, "")
                                    .trim();
                            anthology_title = anthology_title.trim()
                                    .replace("\n", " ")
                                    .replaceAll(CLEANUP_REGEX, "")
                                    .trim();
                            AnthologyTitle anthology = new AnthologyTitle(new Author(anthology_author), anthology_title);
                            mList.add(anthology);
                        }
                        AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) BookEditAnthology.this.getListView().getAdapter());
                        adapter.notifyDataSetChanged();
                        return;
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                this.getResources().getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @SuppressWarnings("EmptyMethod")
                    public void onClick(DialogInterface dialog, int which) {
                        //do nothing
                        return;
                    }
                });
        alertDialog.show();

    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     */
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        MenuItem populate = menu.add(0, POPULATE, 0, R.string.populate_anthology_titles);
        populate.setIcon(android.R.drawable.ic_menu_add);
        super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected. A large switch statement to
     * call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case POPULATE:
                searchWikipedia();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) BookEditAnthology.this.getListView().getAdapter());
                adapter.remove(adapter.getItem((int) info.id));
                mEditManager.setDirty(true);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void saveState(BookData book) {
        if (mSame.isChecked()) {
            anthology_num = DatabaseHelper.ANTHOLOGY_IS_ANTHOLOGY;
        } else {
            anthology_num = DatabaseHelper.ANTHOLOGY_MULTIPLE_AUTHORS ^ DatabaseHelper.ANTHOLOGY_IS_ANTHOLOGY;
        }
        book.setAnthologyTitles(mList);
        book.putInt(ColumnNames.KEY_ANTHOLOGY_MASK, anthology_num);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveState(mEditManager.getBookData());
    }

    @Override
    protected void onLoadBookDetails(BookData book, boolean setAllDone) {
        if (!setAllDone)
            mFields.setAll(book);
    }

    @Override
    protected void onSaveBookDetails(BookData book) {
        super.onSaveBookDetails(book);
        saveState(book);
    }

    private void onListChanged() {
        /* do nothing */
    }

    protected class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {
        boolean series = false;

        /**
         * Pass the parameters directly to the overridden function
         */
        AnthologyTitleListAdapter(Context context, int rowViewId, ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(View target, AnthologyTitle anthology, int position) {
            TextView author = target.findViewById(R.id.row_author);
            author.setText(anthology.getAuthor().getDisplayName());
            TextView title = target.findViewById(R.id.row_title);
            title.setText(anthology.getTitle());
        }

        @Override
        protected void onRowClick(View v, AnthologyTitle anthology, int position) {
            mEditPosition = position;
            mTitleText.setText(anthology.getTitle());
            mAuthorText.setText(anthology.getAuthor().getDisplayName());
            mAdd.setText(R.string.anthology_save);
        }

        @Override
        protected void onListChanged() {
            mEditManager.setDirty(true);
        }
    }
}
