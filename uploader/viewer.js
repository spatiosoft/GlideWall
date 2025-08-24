/* GlideWall simple fullscreen gallery viewer with swipe + keyboard navigation.
 * Features:
 *  - Click (tap) a thumbnail => fullscreen overlay viewer
 *  - Swipe left/right or use arrow keys to navigate
 *  - ESC / background click / close button exits
 *  - Uses native Fullscreen API when available; falls back to fixed overlay
 */
(function(){
  'use strict';

  function ready(fn){ if(document.readyState !== 'loading') fn(); else document.addEventListener('DOMContentLoaded', fn); }

  ready(function(){
    const thumbLinks = Array.from(document.querySelectorAll('.gallery .thumb a'));
    if(!thumbLinks.length) return;
    const imageUrls = thumbLinks.map(a => a.getAttribute('href'));

    let current = 0;
    let overlay, imgEl, captionEl, closeBtn, counterEl;
    let touchStartX = null, touchStartY = null, touchMoved = false;

    thumbLinks.forEach((a, idx) => {
      a.addEventListener('click', e => {
        // Allow middle click / modifier open in new tab.
        if(e.metaKey || e.ctrlKey || e.shiftKey || e.button === 1) return;
        e.preventDefault();
        open(idx);
      });
      // Also allow keyboard enter/space on focused link
      a.addEventListener('keydown', e => {
        if(e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(idx); }
      });
    });

    function buildOverlay(){
      overlay = document.createElement('div');
      overlay.className = 'gw-fs-overlay';
      overlay.setAttribute('role','dialog');
      overlay.setAttribute('aria-modal','true');
      overlay.innerHTML = ''+
        '<div class="gw-fs-stage">\n' +
        '  <button class="gw-fs-close" aria-label="Close">×</button>\n' +
        '  <div class="gw-fs-counter" aria-live="polite"></div>' +
        '  <img class="gw-fs-img" alt="" draggable="false" />\n' +
        '  <div class="gw-fs-cap" aria-live="polite"></div>\n' +
        '  <button class="gw-fs-nav prev" aria-label="Previous (←)">‹</button>' +
        '  <button class="gw-fs-nav next" aria-label="Next (→)">›</button>' +
        '</div>';
      document.body.appendChild(overlay);
      imgEl = overlay.querySelector('.gw-fs-img');
      captionEl = overlay.querySelector('.gw-fs-cap');
      closeBtn = overlay.querySelector('.gw-fs-close');
      counterEl = overlay.querySelector('.gw-fs-counter');
      overlay.addEventListener('click', e => { if(e.target === overlay) close(); });
      closeBtn.addEventListener('click', close);
      overlay.querySelector('.gw-fs-nav.prev').addEventListener('click', prev);
      overlay.querySelector('.gw-fs-nav.next').addEventListener('click', next);

      overlay.addEventListener('touchstart', onTouchStart, {passive:true});
      overlay.addEventListener('touchmove', onTouchMove, {passive:true});
      overlay.addEventListener('touchend', onTouchEnd);
      document.addEventListener('keydown', onKey);
      injectStyles();
    }

    function injectStyles(){
      if(document.getElementById('gw-fs-style')) return;
      const css = `
      .gw-fs-overlay { position:fixed; inset:0; display:flex; align-items:center; justify-content:center; background:#000d; backdrop-filter:blur(2px); z-index:9999; color:#fff; font-family:system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif; }
      .gw-fs-stage { position:relative; max-width:96vw; max-height:96vh; display:flex; flex-direction:column; align-items:center; gap:.75rem; }
      .gw-fs-img { max-width:96vw; max-height:78vh; object-fit:contain; box-shadow:0 0 12px #000a; user-select:none; }
      .gw-fs-close { position:absolute; top:.25rem; right:.25rem; background:#000a; border:0; color:#fff; font-size:2.1rem; line-height:1; width:2.5rem; height:2.5rem; border-radius:50%; cursor:pointer; display:flex; align-items:center; justify-content:center; }
      .gw-fs-close:hover, .gw-fs-nav:hover { background:#222c; }
      .gw-fs-close:focus-visible, .gw-fs-nav:focus-visible { outline:2px solid #5aa2ff; outline-offset:2px; }
      .gw-fs-cap { max-width:90vw; font-size:.8rem; opacity:.85; text-align:center; direction:ltr; word-break:break-all; }
      .gw-fs-nav { position:absolute; top:50%; transform:translateY(-50%); background:#000a; border:0; color:#fff; font-size:2.5rem; width:3rem; height:3rem; border-radius:50%; cursor:pointer; display:flex; align-items:center; justify-content:center; }
      .gw-fs-nav.prev { left:-3.5rem; }
      .gw-fs-nav.next { right:-3.5rem; }
      @media (max-width:640px){ .gw-fs-nav.prev { left:.25rem; } .gw-fs-nav.next { right:.25rem; } .gw-fs-nav { background:#0007; } }
      .gw-fs-counter { position:absolute; top:.5rem; left:.75rem; font-size:.75rem; letter-spacing:.5px; background:#0007; padding:.25rem .5rem; border-radius:.5rem; }
      @media (prefers-color-scheme:light){ .gw-fs-overlay { background:#000c; } }
      `;
      const style = document.createElement('style');
      style.id = 'gw-fs-style';
      style.textContent = css;
      document.head.appendChild(style);
    }

    function requestFs(el){
      const f = el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen;
      if(f) try { f.call(el); } catch(_) {}
    }
    function exitFs(){
      const f = document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen;
      if(f) try { f.call(document); } catch(_) {}
    }

    function open(index){
      current = index;
      if(!overlay) buildOverlay();
      overlay.style.display = 'flex';
      setImage(current);
      // Attempt fullscreen only on user gesture
      requestFs(overlay);
      // Move focus for accessibility
      closeBtn.focus({preventScroll:true});
    }

    function close(){
      if(!overlay) return;
      overlay.style.display = 'none';
      exitFs();
    }

    function setImage(i){
      if(i < 0) i = imageUrls.length - 1; else if(i >= imageUrls.length) i = 0;
      current = i;
      const url = imageUrls[i];
      imgEl.src = url;
      captionEl.textContent = decodeURIComponent(url.split('/').pop());
      counterEl.textContent = (i+1) + ' / ' + imageUrls.length;
      // Preload neighbors for smoother nav
      preload((i+1)%imageUrls.length);
      preload((i-1+imageUrls.length)%imageUrls.length);
    }

    function preload(i){
      const u = imageUrls[i];
      const img = new Image();
      img.src = u;
    }

    function next(){ setImage(current+1); }
    function prev(){ setImage(current-1); }

    function onKey(e){
      if(!overlay || overlay.style.display === 'none') return;
      switch(e.key){
        case 'ArrowRight': case 'Right': next(); break;
        case 'ArrowLeft': case 'Left': prev(); break;
        case 'Escape': close(); break;
        case 'Home': setImage(0); break;
        case 'End': setImage(imageUrls.length-1); break;
      }
    }

    function onTouchStart(e){
      if(e.touches.length !== 1) return;
      touchMoved = false;
      touchStartX = e.touches[0].clientX;
      touchStartY = e.touches[0].clientY;
    }
    function onTouchMove(e){
      if(touchStartX == null) return;
      const dx = e.touches[0].clientX - touchStartX;
      const dy = e.touches[0].clientY - touchStartY;
      if(Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) touchMoved = true;
    }
    function onTouchEnd(e){
      if(touchStartX == null) return;
      const endX = (e.changedTouches && e.changedTouches[0]) ? e.changedTouches[0].clientX : touchStartX;
      const dx = endX - touchStartX;
      const TH = 40; // swipe threshold
      if(touchMoved && Math.abs(dx) > TH){
        if(dx < 0) next(); else prev();
      }
      touchStartX = touchStartY = null; touchMoved = false;
    }

    // Expose minimal API (debug / integration)
    window.GlideWallViewer = { open, close, next, prev };
  });
})();

