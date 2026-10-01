(function () {
  function initReveal(root = document) {
    const revealEls = root.querySelectorAll('.reveal');
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.15, rootMargin: '0px 0px -60px 0px' },
    );
    revealEls.forEach((el) => observer.observe(el));
  }

  window.VAP = window.VAP || {};
  window.VAP.initReveal = initReveal;
})();
