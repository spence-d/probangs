# probangs

> Bangs are that on which the world hangs  
> I'm only holding your hand  
> So I can look at your bangs  
> —They Might Be Giants

**probangs** */ˈpɹoʊbeɪ̯ŋz/* is an extension of search bang syntax for power users.

**!Pro Search** */beɪ̯ŋˈpɹoʊ/* is a web page for routing probang queries,
à la [Unduck](https://github.com/T3-Content/unduck).
It serves as a reference implementation for probangs.

## Usage

!Pro Search can be used in various ways, each with pros and cons:

### Implementations

- [GitHub Page](https://spence-d.github.io/probangs)
  - **Pros**: No setup. Accessible anywhere.
  - **Cons**: No suggestions. Extra network hop to GH's servers.
- Local HTML document
  - *Download links provided in the link panel on the !Pro page*
  - **Pros**: Private, fast
  - **Cons**: No suggestions. Configuring as a search engine and storing
  persistent data may not work in browsers like Firefox
- Self-hosted
  - *See [Building](#building) for how to set this up*
  - **Pros**: Private, fast. Offer search suggestions.
  - **Cons**: Gotta deal with firewalls.

Otherwise, it's a simple script that can be pulled into a personal startpage,
or used as a library that can be incorporated into a Clojure or JVM project.

### Invoking

- Browser search bar
  - **Pros**: Simple, fast
  - **Cons**: Reference URL, multiple bangs, and bang suggestions are unsupported.
  - NB: Search suggestions can be also be configured, depending on the host
    and the browser, but the "only on tab press" feature won't work here.
- Homepage/Bookmark
  - **Cons**: No reference URL.
- Bookmarklet
  - **Pros**: Reference URL passed in for implicit at syntax.
    Optionally populates search bar with selected text.
  - **Cons**: Only works when looking at a web page

### Building
To build the HTML file:

```sh
lein run
```

To build the JavaScript file:

```sh
lein cljsbuild once
```

To run it in a Jetty server:

```sh
lein ring server-headless
```

## Probang syntax
- `![key][@[site_key]]`
- `key![tag=]value[@[site_key]]`

### Classic bangs

probangs is a superset of classic bangs, so all the bangs you know and love
will still work.
`!w` goes to the Wikipedia landing page, `!w search engine` goes to the
Wikipedia entry on search engines, `site:reddit.com cat memes !g` searches
Reddit for cat memes through Google, etc.

There's also the "lucky bang", which emulates Google's "I'm Feeling Lucky" button,
effectively going straight to the first search result.
This is accomplished with a bang symbol in isolation, e.g. `! yahoo` will
almost certainly bring you directly to yahoo.com.

Unlike many search engines, bangs can be put anywhere in the search string and
will be parsed out: `!brv search engine`, `search !brv engine`,
`search engine !brv` all search Brave for "search engine".

Another new way classic bangs can be used differently is that two or more bangs
are permitted. `!w !wes !wt armadillo` will open the articles about armadillos in
English and Spanish wikipedia, and English Wiktionary, each in its own tab.

### Parametrized bangs

Traditionally, if you wanted to search one site in multiple ways, you had to
memorize multiple bang keys, assuming they exist.
For example, `!me_irl` and `!programmerhumor` both search different parts of Reddit.

These keys still exist in probangs, but you can now use parameters by placing
the bang between the primary key and the secondary tags.
So a more flexible way to accomplish the Reddit tags above would be
`r!sub=me_irl` or `r!sub=programmerhumor`, or any other
subreddit you can think of.
Tags names are specific to the bang key, so the traditional Wikipedia keys for
English, French, and Esperanto: `!w`, `!wfr`, `!weo` can be changed to
`w!lang=en`, `w!lang=fr`, and `w!lang=eo`.

But that's a lot of typing! Most keys will only have one tag, so if you omit
the equals sign, the tag name is assumed: `r!me_irl`, `r!programmerhumor`,
`w!en`, `w!fr`, `w!eo`.

### Site searches

A common way to search websites is to add the `site:` keyword into your search.
`!ec site:stackoverflow.com exit vim` will search Ecosia for vim-closing strategies.
Similarly, there are a handful of classic keys to save typing, like `!sog` `!sod`
`!greddit` `!ddgr`, which search StackOverflow on Google and DuckDuckGo, and
Reddit on Google and DuckDuckGo, respectively. Those keys are still there if you
need them, but they can be taxing on the memory and inflexible if you want to change
the site or the search engine.

Enter *ats*: Adding an `@` at the end of the bang will use the bang's base URL as
the `site:` key with any search engine specified after it. So, say I wanted to
perform the above vim search on Ecosia, Google, and DuckDuckGo simultaneously:
`!so@ec !so@g !so@ddg exit vim`

Combining ats with parameters works as well. `r!searchengines@brv bing` will
search reddit.com/r/searchengines about Bing using Brave Search.

At syntax can be shortened further in two ways.

Leaving the part after the @ empty will simply use your default search engine.
`!r@ cat memes` or `r!eyebleach@ puppies` will search "site:reddit.com cat memes"
or "site:reddit.com/r/eyebleach puppies" in whichever search engine you've configured.
See below on how to change the default engine in !Pro.

Leaving the part between ! and @ empty will search the current website.
For example, if I search `!@g exit vim` while browsing StackOverflow,
it will google "site:stackoverflow.com" implicitly.
!Pro requires the bookmarklet to provide the context for this.

Lastly, you can leave both parts implicit. `!@ query` will search the site you're
currently viewing with the default search engine.

## !Pro Features

Apart from probangs, !Pro Search offers a few extra quality-of-life improvements.

- *Fast*: Like Unduck, all bang processing is done client-side in a cached
  JavaScript file, avoiding unnecessary network traffic.
- *Non-volatile, locally-stored settings*: Close the browser and come back,
  !Pro will remember how you liked it, with nothing shared with the server.
- *Non-volatile, locally-stored search history*: Use the up/down arrows to
  quickly re-enter previous queries, even if they were entered in the browser's
  search bar. Any queries that begin with spaces will be discreetly stricken
  from the record, no questions asked!
- *Bookmarklet*: Implicitly makes the current URL available for @ syntax.
  Also lets you select text from a website to search.
- *Bang suggestions*: Will automatically suggest bangs as you type them. If you type
  any capital letters, it will suggest bangs based on their names, i.e. typing
  "!Moz" will suggest Mozilla bangs like `!js`, `!css`, `!dom`.
- *Search suggestions*: If enabled, it will query your default search engine for
  autocomplete suggestions. If any bangs are specified, the first one with a
  suggestion URL will be queried. The bangs are removed from the backend
  request and quietly replaced when presented to the user.
  This can also be used in the browser search bar, if supported.

### Configuring

Configurable options:
- *Autofocus*: If enabled, the keyboard focus is placed on the search bar when
  the page is loaded. This gets you searching faster, but can be annoying if
  used as a generic startpage, because it takes focus away from the location bar.
- *Suggestions*:
  - "Always" will query the search engine's suggestion API with every keystroke.
  - "On Tab Press" will only query the search engine if the user presses the tab key.
  - "Never" disables search suggestions entirely.
  - The tab-only option is recommended to reduce traffic and increase privacy.
  - Only available on dynamically hosted instances.
  - This does not affect bang suggestions.
- *History*: The maximum number of past searches to keep track of. 0 disables
  search history entirely.
- *Lucky*: The bang key to default to on an "I'm Feeling Lucky" search. Only
  "google" and "duckduckgo" support this.
- *Default*: The search engine to be used when no bang is specified, or when an @
  is provided with nothing after it. If more than one is provided, as in
  "brave duckduckgo ecosia", then one will be selected randomly each time.

Configuration can be set in a number of ways. !Pro goes through the following steps
to establish settings.
1. Loads the default config block in the underlying Clojure code.
   No need to modify this, but it's an option if you're self-hosting.
2. Merges in an EDN string in the first script tag of the HTML document.
   These are meant to be site-specific configurations, like disabling suggestions
   on an unsupported server, or setting region-specific default search engines.
   If you're using the files locally, modifying this is a more stable option,
   since using localStorage on local files is undefined behavior for some browsers.
3. Merges the user configuration from localStorage.
   This is the easiest to modify, since there is an interface on the web page,
   but it may be preferable to modify it at a lower level if you want your
   settings be consistent across all your browser profiles and devices.

## Improvements on bangs.json

A few improvements were made on DuckDuckGo's original json document.
- It has been converted from a list into a map for faster key lookup.
- Several bangs have been amended to support probangs features, e.g.
  lucky search URLS, parameters and their defaults, suggestion URLs.
- Base URLs were fixed for sites that used other search engines or sites
  that require URL paths.
  For example, many base URLs were simply "google.com" (even though it was meant
  to search a specific site), or "reddit.com" (even though it represented a specific
  subreddit).
- Site searches relying on Google or DuckDuckGo have had the Google/DDG URL removed.
  This way, it will use the user's default engine instead, or a specied one using ats.
- Redundant keys have been aliased to a single source. This saves some space,
  but also makes it easier to amend keys. If Wikipedia changes their suggestion
  URL, this can be fixed in !wikipedia and !w, !wi, !wiki, !wen, !w.en and
  !encyclopedia get updated for free.


## License

Copyright © 2026 Spencer Dailey

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
