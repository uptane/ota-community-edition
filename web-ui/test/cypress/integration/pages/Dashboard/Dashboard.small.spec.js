/// <reference types="Cypress" />

context('App Dashboard On Small Screen', () => {
  beforeEach(() => {
    // cy.viewport(1900, 1080);
    cy.viewport('iphone-5');
    // cy.viewport('macbook-15');
    cy.visit('/');
  });

  it('has correct title', () => {
    cy.title().should('be', 'Toradex OTA');
  });
  it('has pretty background', () => {
    cy.get('.dashboard-header')
      .should('have.css', 'background-image')
      .and('match', /^(url\(\"data:image\/svg\+xml).+/);
  });
  it('has pretty logo', () => {
    cy.get('.dashboard-title img')
      .should('have.attr', 'src')
      .and('match', /^(data:image\/svg\+xml).+/);
  });
  it('has OTA Connect text', () => {
    cy.get('.dashboard-title span').should('have.html', '&nbsp;OTA Connect');
  });
  it('has "How it works" header', () => {
    cy.get('.how-it-works .sub-title.title-2').should('have.html', 'How it works');
  });
  it('has "Step 1" sub header', () => {
    cy.get('.how-it-works .steps .step-1 .title-3').should('have.html', 'Step 1');
  });
  it('has "Step 2" sub header', () => {
    cy.get('.how-it-works .steps .step-2 .title-3').should('have.html', 'Step 2');
  });
  it('has "Step 1" title and text', () => {
    cy.get('.how-it-works .steps .step-1 .title-3')
      .next('div')
      .should('contain', 'Boot a Toradex SoM into Toradex Easy Installer right out of the box and select "TorizonCore with Docker".')
      .find('h6')
      .should('have.html', 'Install TorizonCore with Toradex Easy Installer');
  });
  it('has "Step 2" title and text', () => {
    cy.get('.how-it-works .steps .step-2 .title-3')
      .siblings('div')
      .should('contain', 'Manage fleets of Toradex devices and keep them up-to-date with your latest application & TorizonCore releases.')
      .find('h6')
      .should('have.html', 'Manage your Toradex Devices');
  });
  it('has "Step 1" image', () => {
    cy.get('.how-it-works .steps .step-1')
      .find('img')
      .should('have.attr', 'src')
      .and('match', /img\/onboarding-install.svg/);
    // .and('match', /^(data:image\/svg\+xml).+/);
  });
  it('has "Step 2" image', () => {
    cy.get('.how-it-works .steps .step-2')
      .find('img')
      .should('have.attr', 'src')
      .and('be.eq', 'img/onboarding-manage.svg');
  });
  it('has a pretty "GET STARTED" button', () => {
    cy.get('.q-btn__content:contains("Get Started")')
      .parent()
      .should('have.class', 'bg-secondary')
      .and('have.class', 'text-white');
  });
  it('has a toggle button with "NIGHT MODE" text', () => {
    cy.get('.toggle-night-mode')
      // cy.get('.q-toggle__label:contains("NIGHT MODE")')
      // .closest('.q-toggle')
      .should('have.class', 'mr-1')
      .and('have.class', 'mt-1')
      .and('have.class', 'text-white');
    // .and(($button) => {
    //   expect($button.position().left).equal($button.offset().left);
    // })
  });
  it('has a menu button on the left side of the screen', () => {
    cy.get('button[aria-label="Menu"]').should(($button) => {
      expect($button.offset()).deep.equal({ left: 16, top: 8 });
    });
  });
});
