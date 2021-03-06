package cz.voho.wiki.model;

import com.google.common.collect.*;
import cz.voho.wiki.repository.page.WikiPageSourceRepository;

import java.util.*;
import java.util.stream.Collectors;

public class ParsedWikiPages {
    private final SetMultimap<String, String> linksToPage;
    private final SetMultimap<String, String> linksFromPage;
    private final SetMultimap<String, String> quotesByAuthor;
    private final Set<String> todoPages;
    private final Map<String, String> parentPage;
    private final Map<String, Toc> pageToc;
    private final Map<String, ParsedWikiPage> parsedWikiPages;

    public ParsedWikiPages(Iterable<ParsedWikiPage> inputParsedWikiPages) {
        this.linksToPage = Multimaps.newSortedSetMultimap(Maps.newTreeMap(), TreeSet::new);
        this.linksFromPage = Multimaps.newSortedSetMultimap(Maps.newTreeMap(), TreeSet::new);
        this.quotesByAuthor = Multimaps.newSortedSetMultimap(Maps.newTreeMap(), TreeSet::new);
        this.todoPages = Sets.newTreeSet();
        this.parentPage = Maps.newHashMap();
        this.pageToc = Maps.newHashMap();
        this.parsedWikiPages = Maps.newHashMap();

        for (ParsedWikiPage parsedWikiPage : inputParsedWikiPages) {
            parsedWikiPage.getQuotes().forEach(quote -> quotesByAuthor.put(quote.getAuthor(), quote.getText()));

            if (parsedWikiPage.isTodo()) {
                todoPages.add(parsedWikiPage.getSource().getId());
            }

            if (parsedWikiPage.getSource().getParentId() != null) {
                parentPage.put(parsedWikiPage.getSource().getId(), parsedWikiPage.getSource().getParentId());
            }

            parsedWikiPage.getLinkedPages().forEach(targetWikiPageId -> {
                linksFromPage.put(parsedWikiPage.getSource().getId(), targetWikiPageId);
                linksToPage.put(targetWikiPageId, parsedWikiPage.getSource().getId());
            });

            pageToc.put(parsedWikiPage.getSource().getId(), parsedWikiPage.getToc());

            parsedWikiPages.put(parsedWikiPage.getSource().getId(), parsedWikiPage);
        }
    }

    public boolean exists(final String wikiPageId) {
        return parsedWikiPages.containsKey(wikiPageId);
    }

    public ParsedWikiPage getPage(String wikiPageId) {
        return parsedWikiPages.get(wikiPageId);
    }

    public ImmutableList<String> getBreadCrumbs(final String wikiPageId) {
        if (wikiPageId.equals(WikiPageSourceRepository.INDEX_PAGE_ID)) {
            return ImmutableList.of();
        }

        final List<String> result = Lists.newLinkedList();
        result.addAll(getParentPages(wikiPageId));
        return ImmutableList.copyOf(Lists.reverse(result));
    }

    public ImmutableList<String> getSubPages(final String wikiPageId) {
        return ImmutableList.copyOf(parentPage.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(wikiPageId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()));
    }

    public ImmutableSet<String> getWikiPageIds() {
        return ImmutableSet.copyOf(parsedWikiPages.keySet());
    }

    public Toc getNonTrivialToc(final String wikiPageId) {
        Toc toc = pageToc.get(wikiPageId);
        if (toc.isTrivial()) {
            return null;
        }
        return toc;
    }

    public ImmutableSet<String> getLinksFromPage(final String wikiPageId) {
        return nullToEmpty(linksFromPage.get(wikiPageId));
    }

    public ImmutableSet<String> getLinksToPage(final String wikiPageId) {
        return nullToEmpty(linksToPage.get(wikiPageId));
    }

    public List<Missing> getMissingPages() {
        final List<Missing> result = Lists.newLinkedList();

        linksFromPage.entries().forEach(e -> {
            if (!parsedWikiPages.containsKey(e.getValue())) {
                final Missing missing = new Missing();
                missing.setSourceWikiPageId(e.getKey());
                missing.setMissingWikiPageId(e.getValue());
                result.add(missing);
            }
        });

        return result;
    }

    public Set<Quote> getQuotes() {
        return quotesByAuthor
                .entries()
                .stream()
                .map(entry -> {
                    Quote q = new Quote();
                    q.setAuthor(entry.getKey());
                    q.setText(entry.getValue());
                    return q;
                })
                .sorted(Comparator.comparing(Quote::getAuthor))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Todo> getTodoPages() {
        return todoPages
                .stream()
                .map(entry -> {
                    Todo t = new Todo();
                    t.setWikiPageId(entry);
                    return t;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ImmutableSet<String> nullToEmpty(final Set<String> strings) {
        if (strings == null) {
            return ImmutableSet.of();
        }

        return ImmutableSet.copyOf(strings);
    }

    private ImmutableList<String> getParentPages(final String wikiPageId) {
        final List<String> parents = Lists.newLinkedList();

        String tempId = wikiPageId;

        while (tempId != null) {
            parents.add(tempId);
            tempId = parentPage.get(tempId);
        }

        // remove the current page (which is first)
        parents.remove(0);

        return ImmutableList.copyOf(parents);
    }
}
