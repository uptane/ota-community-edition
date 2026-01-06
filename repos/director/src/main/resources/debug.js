import Handlebars from "https://esm.sh/handlebars@4.7.8";

Handlebars.registerHelper("rendertd", function(options) {
  if(typeof this === 'object' && this !== null) {
    return new Handlebars.SafeString('<pre class="code">' + options.fn(JSON.stringify(this, null, 2)) + "</pre>");
  } else {
    return options.fn(this);
  }
});

async function buildNavForm() {
    const navResp = await fetch("/debug/navigation.json");
    const navs = await navResp.json();

   let navItemTempl = document.querySelector("#nav-input-tmpl").innerHTML;
   let navItemFn = Handlebars.compile(navItemTempl);

   navs.links.forEach(v => {
     let html = navItemFn({
        "href": v.href,
        "name": v.name,
        "paramName": v.paramName,
        })

      let container = document.querySelector("#nav-inputs");

      container.insertAdjacentHTML('beforeend', html);
   });
}

async function addDataTables() {
  const indexResp = await fetch((location.pathname+location.search) + "/index.json");

  if(indexResp.status != 200) {
    console.info("Error response from server")
    console.info(indexResp)
    return;
  }

  const indexItems = await indexResp.json();

  let tableTmplStr = document.querySelector("#table-tmpl").innerHTML;
  let tableTemplate = Handlebars.compile(tableTmplStr);

  let navbarItemTemplateStr = document.querySelector("#menu-item-tmpl").innerHTML;
  let navbarItemTemplate = Handlebars.compile(navbarItemTemplateStr);

  document.querySelector("#title").innerHTML = indexItems.title;

  let navbarContainer = document.querySelector("#navbar-section");
  let container = document.querySelector("#tables-container");    

  indexItems.links.forEach(async (v) => {
    let navbarItemHtml = navbarItemTemplate({
            "name": v.name,
            "id": v.id,
    });

    navbarContainer.insertAdjacentHTML('beforeend', navbarItemHtml);

    let response = await fetch(v.href);
    let data = await response.json();

    let tableHtml = tableTemplate({
      name: v.name,
      id: v.id,
      columns: data.columns,
      rows: data.data,
    });

    container.insertAdjacentHTML('beforeend', tableHtml);
  });
}

document.addEventListener("DOMContentLoaded", async function() {
  await buildNavForm();

  await addDataTables();
});

