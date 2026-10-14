// Behaviour shared by all pages. Loaded at the end of <body>, after Bootstrap's bundle (see fragments/footer.html).
// Everything is attached with addEventListener, so the templates need no inline scripts or event handler attributes
// and a Content-Security-Policy without 'unsafe-inline' for scripts is possible.
(function () {
    'use strict';

    // Bootstrap form validation: forms with the class "needs-validation" aren't submitted while invalid and show
    // Bootstrap's validation styles after the first submit attempt
    function initFormValidation() {
        document.querySelectorAll('.needs-validation').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                if (!form.checkValidity()) {
                    event.preventDefault();
                    event.stopPropagation();
                }
                form.classList.add('was-validated');
            }, false);
        });
    }

    // Contracts table: combines the "hide inactive" checkbox (#filter) and the search text (#search). A row is shown
    // only if it passes both. Inactive rows have the class "inactive" and are already hidden by the server
    function initContractsFilter() {
        const filter = document.getElementById('filter');
        const search = document.getElementById('search');
        const table = document.getElementById('contracts');
        if (!filter || !search || !table) {
            return;
        }

        function filterContracts() {
            const hideInactive = filter.checked;
            const text = search.value.toLowerCase();
            for (const row of table.querySelectorAll('tbody tr')) {
                const active = !row.classList.contains('inactive');
                const matches = Array.from(row.getElementsByTagName('td'))
                    .some(function (col) { return col.textContent.toLowerCase().includes(text); });
                row.style.display = (active || !hideInactive) && matches ? '' : 'none';
            }
        }

        filter.addEventListener('change', filterContracts);
        search.addEventListener('input', filterContracts);
        // the browser may restore the checkbox and search text when navigating back to this page
        window.addEventListener('pageshow', filterContracts);
    }

    // Buttons and links with a data-confirm attribute ask for confirmation with that (localized) text first and
    // only proceed on OK
    function initConfirmations() {
        document.querySelectorAll('[data-confirm]').forEach(function (element) {
            element.addEventListener('click', function (event) {
                if (!window.confirm(element.dataset.confirm)) {
                    event.preventDefault();
                }
            });
        });
    }

    // Bootstrap tooltips are opt-in and need to be initialised explicitly
    function initTooltips() {
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (element) {
            new bootstrap.Tooltip(element);
        });
    }

    function init() {
        initFormValidation();
        initContractsFilter();
        initConfirmations();
        initTooltips();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
