import React from 'react'

const downloadPath = '/downloads/IdleGridAgent-windows.zip'

function App() {
  return (
    <main className="page">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="IdleGrid home">
          <span className="brand-mark">IG</span>
          <span>IdleGrid</span>
        </a>
        <span className="network-label"><i /> Campus lab agent</span>
      </header>

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow">Shared campus computing</p>
          <h1>Put lab PCs to work.<br /><em>Keep them usable.</em></h1>
          <p className="intro">
            Install the IdleGrid Agent to contribute capped compute and disk capacity
            to your campus lab pool.
          </p>
          <a className="download" href={downloadPath} download="IdleGridAgent-windows.zip">
            <span className="download-icon" aria-hidden="true">↓</span>
            <span><strong>Download agent for Windows</strong><small>Portable ZIP · 3.1 MB</small></span>
          </a>
          <p className="requirements">Requires Java 17+ and Docker Desktop with WSL2.</p>
        </div>
        <aside className="package-card" aria-label="ZIP package contents">
          <div className="card-top"><span className="file-icon">ZIP</span><span className="file-type">WINDOWS PACKAGE</span></div>
          <h2>Ready to extract</h2>
          <p>Everything needed to start the agent is included.</p>
          <ul>
            <li><b>01</b><span>Agent runtime JAR</span><span className="check">✓</span></li>
            <li><b>02</b><span>One-click BAT launcher</span><span className="check">✓</span></li>
            <li><b>03</b><span>Master connection settings</span><span className="check">✓</span></li>
            <li><b>04</b><span>Setup script and quick start</span><span className="check">✓</span></li>
          </ul>
        </aside>
      </section>

      <section className="steps" aria-labelledby="steps-title">
        <div className="section-heading"><p className="eyebrow">Three quick steps</p><h2 id="steps-title">Join the lab grid</h2></div>
        <div className="step-grid">
          <article><span className="step-no">01</span><h3>Download & extract</h3><p>Save the ZIP and extract all files into one folder on the lab PC.</p></article>
          <article><span className="step-no">02</span><h3>Set up the PC</h3><p>Install Java 17+ and Docker Desktop. Run <code>setup.ps1</code> as Administrator if WSL2 or Docker needs setup.</p></article>
          <article><span className="step-no">03</span><h3>Start the agent</h3><p>Open <code>agent.properties</code> if needed, then double-click <code>run-agent.bat</code>.</p></article>
        </div>
      </section>

      <section className="connection-note">
        <span className="note-dot" />
        <p><strong>Campus network:</strong> The package uses <code>master.url=auto</code> for LAN discovery. If discovery is disabled, set it to <code>http://&lt;master-ip&gt;:9090</code>.</p>
        <a href={downloadPath} download="IdleGridAgent-windows.zip">Get the ZIP ↗</a>
      </section>

      <footer><span>IdleGrid · Campus lab compute</span><span>Windows agent package</span></footer>

      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap');
        :root { color-scheme: light; font-family: 'DM Sans', sans-serif; background: #f4f1eb; color: #201e1b; }
        * { box-sizing: border-box; }
        body { margin: 0; }
        .page { max-width: 1160px; padding: 0 32px; margin: 0 auto; }
        .topbar { height: 82px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #ded9d0; }
        .brand { display:flex; gap:11px; align-items:center; color:#201e1b; font:700 20px 'Space Grotesk',sans-serif; text-decoration:none; }
        .brand-mark { width:34px;height:34px;display:grid;place-items:center;background:#ed6b35;color:#fff;border-radius:10px;font:700 12px 'DM Mono',monospace; }
        .network-label,.eyebrow,.file-type { font:500 10px 'DM Mono',monospace; text-transform:uppercase; letter-spacing:.13em; }
        .network-label { display:flex;align-items:center;gap:9px;color:#716c64; }
        .network-label i,.note-dot { width:7px;height:7px;border-radius:50%;background:#42a77a;box-shadow:0 0 0 4px #42a77a1b; }
        .hero { display:grid;grid-template-columns:1.25fr .75fr;gap:70px;align-items:center;padding:90px 0 86px; }
        .eyebrow { color:#dc6330;margin:0 0 20px; }
        h1,h2,h3,p { margin-top:0; }
        h1 { font:600 clamp(42px,6vw,70px)/1.02 'Space Grotesk',sans-serif;letter-spacing:-.055em;margin-bottom:24px; }
        h1 em { color:#dc6330;font-style:normal; }
        .intro { color:#68635d;line-height:1.7;max-width:490px;font-size:16px;margin-bottom:28px; }
        .download { display:inline-flex;align-items:center;gap:14px;background:#dc6330;color:#fff;text-decoration:none;padding:14px 20px;border-radius:12px;box-shadow:0 9px 24px #dc63302a;transition:transform .18s,background .18s; }
        .download:hover { transform:translateY(-2px);background:#c95324; }
        .download-icon { width:34px;height:34px;border-radius:9px;background:#ffffff24;display:grid;place-items:center;font-size:23px; }
        .download strong,.download small { display:block; }
        .download strong { font-size:14px; }
        .download small { font:10px 'DM Mono',monospace;color:#ffe1d2;margin-top:4px; }
        .requirements { color:#89837b;font-size:11px;margin:13px 0 0 2px; }
        .package-card { background:#fffdfa;border:1px solid #e4ded5;border-radius:18px;padding:26px;box-shadow:0 20px 60px #211c1410; }
        .card-top { display:flex;align-items:center;gap:12px;margin-bottom:27px; }
        .file-icon { display:grid;place-items:center;width:43px;height:43px;border-radius:12px;background:#fcede5;color:#dc6330;font:500 12px 'DM Mono',monospace; }
        .file-type { color:#989188;font-size:9px; }
        .package-card h2 { font:600 24px 'Space Grotesk',sans-serif;margin-bottom:7px; }
        .package-card p { color:#807a72;font-size:12px;margin-bottom:20px; }
        .package-card ul { padding:0;margin:0;list-style:none;border-top:1px solid #eee9e1; }
        .package-card li { display:grid;grid-template-columns:28px 1fr 18px;gap:8px;align-items:center;padding:12px 0;border-bottom:1px solid #eee9e1;font-size:12px; }
        .package-card li b { font:10px 'DM Mono',monospace;color:#b6afa5; }
        .check { color:#42a77a;font-weight:700; }
        .steps { border-top:1px solid #ded9d0;padding:55px 0 62px; }
        .section-heading { display:flex;align-items:baseline;gap:20px;margin-bottom:28px; }
        .section-heading .eyebrow { margin:0; }
        .section-heading h2 { font:600 27px 'Space Grotesk',sans-serif;margin:0;letter-spacing:-.03em; }
        .step-grid { display:grid;grid-template-columns:repeat(3,1fr);gap:28px; }
        .step-grid article { border-top:2px solid #e4ded5;padding-top:16px; }
        .step-no { font:10px 'DM Mono',monospace;color:#dc6330; }
        .step-grid h3 { font:600 16px 'Space Grotesk',sans-serif;margin:14px 0 8px; }
        .step-grid p { color:#777168;font-size:12px;line-height:1.7;margin:0; }
        code { font:11px 'DM Mono',monospace;color:#a94d27;background:#f2ece3;padding:2px 5px;border-radius:4px; }
        .connection-note { display:flex;align-items:center;gap:14px;padding:17px 18px;border:1px solid #dfe8dd;background:#edf5ed;border-radius:12px;margin-bottom:48px; }
        .connection-note p { flex:1;color:#5f6c5f;font-size:11px;line-height:1.6;margin:0; }
        .connection-note strong { color:#364a39; }
        .connection-note a { color:#436c4c;font:10px 'DM Mono',monospace;white-space:nowrap;text-decoration:none; }
        footer { border-top:1px solid #ded9d0;padding:22px 0 30px;display:flex;justify-content:space-between;color:#938d84;font:9px 'DM Mono',monospace;text-transform:uppercase;letter-spacing:.1em; }
        @media (max-width:760px) { .page{padding:0 20px}.hero{grid-template-columns:1fr;gap:38px;padding:62px 0}.package-card{max-width:520px}.step-grid{grid-template-columns:1fr;gap:22px}.section-heading{display:block}.section-heading h2{margin-top:10px}.connection-note{align-items:flex-start;flex-wrap:wrap}.connection-note p{flex-basis:calc(100% - 25px)}.connection-note a{margin-left:21px} }
        @media (max-width:420px) { .network-label{font-size:8px}.hero{padding-top:48px}h1{font-size:42px}.download{width:100%;padding:12px}.download strong{font-size:12px}footer{gap:15px;flex-direction:column} }
      `}</style>
    </main>
  )
}

export default App
