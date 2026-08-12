import type { CategoryKey } from '../styles/theme'

/* ── Shared wrapper style ─────────────────────────────────────────────── */

const bgStyle: React.CSSProperties = {
  position: 'absolute', inset: 0,
  width: '100%', height: '100%',
  pointerEvents: 'none', overflow: 'hidden'
}

/* ── Wave path helper ─────────────────────────────────────────────────── */

function wave(baseY: number, amp: number, freq: number, phase: number): string {
  let d = `M 0 ${baseY}`
  for (let x = 0; x <= 1000; x += 8)
    d += ` L ${x} ${(baseY + Math.sin((x + phase) * Math.PI / freq) * amp).toFixed(1)}`
  return d + ' L 1000 1000 L 0 1000 Z'
}

/* ══════════════════════════════════════════════════════════════════════════
   Dashboard Background
   ══════════════════════════════════════════════════════════════════════ */

export function DashboardBackground() {
  return (
    <svg style={bgStyle} viewBox="0 0 1000 1000" preserveAspectRatio="none">
      {/* Watercolor wash blobs */}
      <circle cx={150} cy={200} r={350} fill="#ED93B1" opacity={0.08} />
      <circle cx={800} cy={550} r={300} fill="#5DCAA5" opacity={0.06} />
      <circle cx={500} cy={750} r={250} fill="#F0997B" opacity={0.06} />
      <circle cx={300} cy={500} r={280} fill="#DEDCF7" opacity={0.08} />
      <circle cx={700} cy={250} r={220} fill="#F5E5C4" opacity={0.07} />

      {/* Misty mountain layers */}
      <path d="M 0 720 C 120 650 280 680 420 640 C 580 600 750 660 1000 700 L 1000 1000 L 0 1000 Z" fill="#7BAFD4" opacity={0.08} />
      <path d="M 0 780 C 200 720 400 750 600 730 C 800 700 900 760 1000 780 L 1000 1000 L 0 1000 Z" fill="#7BAFD4" opacity={0.10} />
      <path d="M 0 860 C 250 800 550 830 750 810 C 900 790 1000 840 1000 880 L 1000 1000 L 0 1000 Z" fill="#7BAFD4" opacity={0.12} />

      {/* Mt. Fuji */}
      <path d="M 250 880 C 320 820 420 720 500 640 C 580 720 680 820 750 880 Z" fill="#5A7EA0" opacity={0.15} />
      <path d="M 420 730 L 500 640 L 580 730 C 560 740 530 720 500 740 C 470 720 440 740 420 730 Z" fill="white" opacity={0.22} />
      <rect x={300} y={760} width={400} height={15} rx={8} fill="white" opacity={0.10} />

      {/* Stone lantern */}
      <line x1={80} y1={900} x2={80} y2={820} stroke="#5A7EA0" strokeWidth={4} opacity={0.13} />
      <path d="M 30 820 L 80 780 L 130 820 Z" fill="#5A7EA0" opacity={0.13} />
      <rect x={60} y={820} width={40} height={20} rx={2} fill="#F5E5C4" opacity={0.12} />

      {/* Torii gate */}
      <line x1={820} y1={800} x2={820} y2={950} stroke="#D4728E" strokeWidth={5} opacity={0.22} />
      <line x1={960} y1={800} x2={960} y2={950} stroke="#D4728E" strokeWidth={5} opacity={0.22} />
      <path d="M 780 805 Q 800 790 820 800 L 960 800 Q 980 790 1000 805 L 1000 815 L 780 815 Z" fill="#D4728E" opacity={0.22} />
      <line x1={800} y1={835} x2={980} y2={835} stroke="#D4728E" strokeWidth={3} opacity={0.22} />

      {/* Sakura tree */}
      <line x1={920} y1={950} x2={900} y2={500} stroke="#8B6B5A" strokeWidth={6} opacity={0.15} />
      <line x1={900} y1={500} x2={780} y2={350} stroke="#8B6B5A" strokeWidth={4} opacity={0.15} />
      <line x1={900} y1={550} x2={980} y2={380} stroke="#8B6B5A" strokeWidth={3} opacity={0.15} />
      <line x1={840} y1={420} x2={750} y2={320} stroke="#8B6B5A" strokeWidth={2} opacity={0.10} />
      <line x1={860} y1={380} x2={920} y2={280} stroke="#8B6B5A" strokeWidth={1.5} opacity={0.10} />

      {/* Blossom clusters */}
      {[[780,340,14],[740,310,10],[820,360,8],[980,360,12],[950,320,9],[820,490,10],[920,260,8],[750,300,7]].map(
        ([x,y,r], i) => <circle key={`bc${i}`} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.26} />
      )}

      {/* Scattered petals */}
      {[[60,60,10],[120,30,7],[180,80,5],[300,40,6],[450,20,4],[550,60,5],[650,100,4],[400,120,3]].map(
        ([x,y,r], i) => <circle key={`sp${i}`} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.26} />
      )}
      {[[700,140,4],[250,100,3],[500,80,4],[150,140,3]].map(
        ([x,y,r], i) => <circle key={`lp${i}`} cx={x} cy={y} r={r} fill="#F5C4D8" opacity={0.20} />
      )}

      {/* Cloud wisps */}
      <rect x={20} y={180} width={70} height={10} rx={5} fill="white" opacity={0.10} />
      <rect x={350} y={220} width={55} height={8} rx={4} fill="white" opacity={0.10} />
      <rect x={600} y={160} width={45} height={9} rx={4} fill="white" opacity={0.10} />
    </svg>
  )
}

/* ══════════════════════════════════════════════════════════════════════════
   Category Backgrounds
   ══════════════════════════════════════════════════════════════════════ */

export function CategoryBackground({ categoryKey }: { categoryKey: CategoryKey }) {
  return (
    <svg style={bgStyle} viewBox="0 0 1000 1000" preserveAspectRatio="none">
      {bg[categoryKey]?.()}
    </svg>
  )
}

const bg: Record<string, () => React.ReactNode> = {

  /* ── Water: Waves, Koi, Ripples, Bamboo, Petals ──────────────────── */
  water: () => <>
    {/* Mist hills */}
    <path d="M 0 250 C 150 180 350 220 500 200 C 700 170 850 230 1000 210 L 1000 300 C 800 280 500 320 0 270 Z" fill="#85B7EB" opacity={0.12} />

    {/* Wave layers */}
    {[0,1,2,3].map(i =>
      <path key={i} d={wave(700+i*70, 18-i*3, 100+i*30, i*60)} fill="#85B7EB" opacity={0.18 - i*0.03} />
    )}

    {/* Koi fish (simplified teardrop + tail) */}
    <ellipse cx={250} cy={750} rx={18} ry={10} fill="#F0997B" opacity={0.16} transform="rotate(-17 250 750)" />
    <polygon points="232,750 222,742 222,758" fill="#F0997B" opacity={0.16} />
    <ellipse cx={650} cy={820} rx={14} ry={8} fill="#F0997B" opacity={0.13} transform="rotate(28 650 820)" />
    <polygon points="636,820 628,813 628,827" fill="#F0997B" opacity={0.13} />

    {/* Ripples */}
    {[12,22,34].map(r => <circle key={`r1${r}`} cx={300} cy={680} r={r} fill="none" stroke="#2A5A80" strokeWidth={1.2} opacity={0.10} />)}
    {[8,16,26].map(r => <circle key={`r2${r}`} cx={750} cy={720} r={r} fill="none" stroke="#2A5A80" strokeWidth={1.2} opacity={0.10} />)}

    {/* Cherry petals */}
    {[[880,60,10],[820,100,7],[140,120,8],[80,180,6],[500,40,5],[350,140,4],[720,160,5]].map(
      ([x,y,r],i) => <circle key={i} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.20} />
    )}

    {/* Bamboo stalk */}
    <line x1={40} y1={1000} x2={40} y2={150} stroke="#5A8EB0" strokeWidth={7} opacity={0.14} />
    {[350,500,650,800].map(y => <line key={y} x1={20} y1={y} x2={60} y2={y} stroke="#5A8EB0" strokeWidth={2} opacity={0.14} />)}
    <path d="M 40 350 Q 15 325 -5 310 Q 15 310 40 350 Z" fill="#5A8EB0" opacity={0.14} />
    <path d="M 40 500 Q 65 475 80 460 Q 65 480 40 500 Z" fill="#5A8EB0" opacity={0.14} />
  </>,

  /* ── Food: Bamboo Grove, Noren, Steam, Chopsticks ────────────────── */
  food: () => <>
    {/* Bamboo grove */}
    {[[880,120,8],[930,200,6.5],[960,280,5]].map(([x,top,w],i) =>
      <g key={i}>
        <line x1={x} y1={1000} x2={x} y2={top} stroke="#27500A" strokeWidth={w} opacity={0.12} />
        {[0.25,0.5,0.75].map(f => {
          const ny = top + (1000-top)*f
          return <line key={f} x1={x-6} y1={ny} x2={x+6} y2={ny} stroke="#27500A" strokeWidth={2} opacity={0.12} />
        })}
        <path d={`M ${x} ${top+(1000-top)*0.25} Q ${x + (i%2===0?-25:25)} ${top+(1000-top)*0.2} ${x + (i%2===0?-40:40)} ${top+(1000-top)*0.18}`}
          fill="none" stroke="#27500A" strokeWidth={2} opacity={0.10} />
      </g>
    )}

    {/* Noren curtain */}
    <rect x={50} y={0} width={550} height={15} rx={4} fill="#27500A" opacity={0.12} />
    {[0,1,2,3,4].map(i => {
      const cx = 80 + i * 110
      return <path key={i} d={`M ${cx} 15 C ${cx-8} 40 ${cx+8} 70 ${cx-3} 100 L ${cx+57} 100 C ${cx+68} 70 ${cx+52} 40 ${cx+60} 15 Z`}
        fill="#27500A" opacity={0.09} />
    })}

    {/* Steam wisps */}
    {[150,300].map(x =>
      <path key={x} d={`M ${x} 920 C ${x-12} 870 ${x+15} 820 ${x-8} 770 C ${x-20} 730 ${x+10} 680 ${x} 640`}
        fill="none" stroke="#27500A" strokeWidth={3} opacity={0.07} />
    )}

    {/* Chopsticks */}
    <line x1={200} y1={920} x2={300} y2={800} stroke="#27500A" strokeWidth={2.5} opacity={0.08} />
    <line x1={220} y1={920} x2={320} y2={800} stroke="#27500A" strokeWidth={2.5} opacity={0.08} />
  </>,

  /* ── Sleep: Moon, Stars, Mountains, Clouds, Lantern ──────────────── */
  sleep: () => <>
    {/* Mountain silhouettes */}
    <path d="M 0 800 C 150 680 300 740 450 700 C 550 670 700 720 850 680 C 950 650 1000 700 1000 750 L 1000 1000 L 0 1000 Z" fill="#3C3489" opacity={0.12} />
    <path d="M 0 870 C 200 800 500 840 700 820 C 850 800 950 850 1000 880 L 1000 1000 L 0 1000 Z" fill="#3C3489" opacity={0.09} />

    {/* Moon glow + crescent */}
    <circle cx={800} cy={80} r={55} fill="white" opacity={0.12} />
    <circle cx={800} cy={80} r={38} fill="white" opacity={0.20} />
    <circle cx={830} cy={65} r={30} fill="#CCC8EE" opacity={0.95} />

    {/* Cloud wisps */}
    {[[100,140,80],[550,200,60],[300,100,50]].map(([x,y,w],i) => <g key={i}>
      <rect x={x} y={y} width={w} height={12} rx={6} fill="white" opacity={0.10} />
      <rect x={x+w*0.2} y={y-8} width={w*0.6} height={10} rx={5} fill="white" opacity={0.10} />
    </g>)}

    {/* Stars */}
    {[[120,40,3.5],[280,80,2.5],[480,30,4],[620,110,2],[80,220,2.5],[380,160,3],[720,50,2],[920,180,3],[550,130,1.5],[200,190,2]].map(
      ([x,y,r],i) => <g key={i}>
        <circle cx={x} cy={y} r={r} fill="white" opacity={r > 2.5 ? 0.22 : 0.14} />
        {r >= 3.5 && <>
          <line x1={x-r*2.5} y1={y} x2={x+r*2.5} y2={y} stroke="white" strokeWidth={0.8} opacity={0.22} />
          <line x1={x} y1={y-r*2.5} x2={x} y2={y+r*2.5} stroke="white" strokeWidth={0.8} opacity={0.22} />
        </>}
      </g>
    )}

    {/* Paper lantern */}
    <line x1={120} y1={670} x2={120} y2={700} stroke="#3C3489" strokeWidth={1} opacity={0.08} />
    <ellipse cx={120} cy={716} rx={12} ry={16} fill="#F5E5C4" opacity={0.15} />
    <circle cx={120} cy={716} r={25} fill="#F5E5C4" opacity={0.08} />
  </>,

  /* ── Emotions: Sakura Tree, Petals, Wind, Hills ──────────────────── */
  emotions: () => <>
    {/* Rolling hills */}
    <path d="M 0 880 C 200 820 400 860 600 830 C 800 800 900 850 1000 870 L 1000 1000 L 0 1000 Z" fill="#633806" opacity={0.07} />

    {/* Sakura tree */}
    <line x1={820} y1={880} x2={780} y2={350} stroke="#633806" strokeWidth={8} opacity={0.16} />
    <line x1={780} y1={350} x2={600} y2={180} stroke="#633806" strokeWidth={5} opacity={0.16} />
    <line x1={780} y1={450} x2={920} y2={250} stroke="#633806" strokeWidth={4} opacity={0.16} />
    <line x1={780} y1={550} x2={650} y2={400} stroke="#633806" strokeWidth={3} opacity={0.11} />
    <line x1={680} y1={260} x2={550} y2={150} stroke="#633806" strokeWidth={2} opacity={0.11} />
    <line x1={720} y1={220} x2={780} y2={120} stroke="#633806" strokeWidth={2} opacity={0.11} />
    <line x1={880} y1={300} x2={950} y2={180} stroke="#633806" strokeWidth={2} opacity={0.11} />

    {/* Blossom clusters */}
    {[[600,170,16],[550,140,12],[640,200,10],[920,240,14],[960,200,10],[880,280,11],[650,390,12],[620,430,9],[780,110,10],[950,160,8]].map(
      ([x,y,r],i) => <circle key={`b${i}`} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.22} />
    )}
    {[[590,160,8],[930,230,7],[640,400,6]].map(
      ([x,y,r],i) => <circle key={`bl${i}`} cx={x} cy={y} r={r} fill="#F5C4D8" opacity={0.16} />
    )}

    {/* Scattered petals */}
    {[[100,200,5],[220,350,4],[180,500,6],[350,280,3],[420,450,5],[80,650,4],[300,600,3],[500,550,4],[150,780,5],[400,720,3]].map(
      ([x,y,r],i) => <circle key={`p${i}`} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.18} />
    )}

    {/* Wind lines */}
    <line x1={50} y1={300} x2={150} y2={280} stroke="#633806" strokeWidth={1.5} opacity={0.06} />
    <line x1={250} y1={420} x2={380} y2={400} stroke="#633806" strokeWidth={1.5} opacity={0.06} />
    <line x1={100} y1={550} x2={220} y2={530} stroke="#633806" strokeWidth={1.5} opacity={0.06} />
  </>,

  /* ── Bathroom: Zen Garden, Raked Sand, Shishi-odoshi ─────────────── */
  bathroom: () => <>
    {/* Raked sand lines */}
    {Array.from({length:13}, (_,i) => 650 + i*18).map(y =>
      <line key={y} x1={0} y1={y} x2={1000} y2={y} stroke="#72243E" strokeWidth={0.8} opacity={0.07} />
    )}

    {/* Raked circles around stones */}
    {[1,2,3,4,5,6].map(r => <circle key={`s1${r}`} cx={650} cy={780} r={18+r*14} fill="none" stroke="#72243E" strokeWidth={0.8} opacity={0.07} />)}
    {[1,2,3,4].map(r => <circle key={`s2${r}`} cx={250} cy={820} r={12+r*12} fill="none" stroke="#72243E" strokeWidth={0.8} opacity={0.07} />)}

    {/* Stones */}
    <ellipse cx={650} cy={780} rx={18} ry={12} fill="#72243E" opacity={0.14} />
    <ellipse cx={672} cy={774} rx={8} ry={6} fill="#72243E" opacity={0.11} />
    <ellipse cx={250} cy={820} rx={14} ry={10} fill="#72243E" opacity={0.14} />
    <circle cx={450} cy={850} r={6} fill="#72243E" opacity={0.11} />

    {/* Shishi-odoshi */}
    <line x1={900} y1={450} x2={900} y2={650} stroke="#72243E" strokeWidth={4} opacity={0.11} />
    <line x1={850} y1={500} x2={950} y2={500} stroke="#72243E" strokeWidth={3} opacity={0.11} />
    <line x1={950} y1={500} x2={950} y2={560} stroke="#85B7EB" strokeWidth={1.5} opacity={0.10} />
    <ellipse cx={950} cy={564} rx={8} ry={4} fill="#72243E" opacity={0.11} />

    {/* Bamboo fence */}
    {Array.from({length:9}, (_,i) => 20+i*40).map(x =>
      <line key={x} x1={x} y1={80} x2={x} y2={180} stroke="#72243E" strokeWidth={2.5} opacity={0.08} />
    )}
    <line x1={20} y1={110} x2={340} y2={110} stroke="#72243E" strokeWidth={1.5} opacity={0.08} />
    <line x1={20} y1={150} x2={340} y2={150} stroke="#72243E" strokeWidth={1.5} opacity={0.08} />
  </>,

  /* ── Health: Bonsai, Lotus Pond, Stepping Stones ─────────────────── */
  health: () => <>
    {/* Stepping stones */}
    {[[80,920,28,18],[180,870,24,16],[300,900,26,17],[420,850,22,15]].map(
      ([x,y,rx,ry],i) => <ellipse key={i} cx={x} cy={y} rx={rx/2} ry={ry/2} fill="#085041" opacity={0.09} />
    )}

    {/* Bonsai pot */}
    <rect x={720} y={620} width={120} height={30} rx={4} fill="#085041" opacity={0.12} />
    {/* Trunk */}
    <path d="M 780 620 C 760 550 800 480 770 400" fill="none" stroke="#085041" strokeWidth={6} opacity={0.15} />
    <line x1={770} y1={450} x2={680} y2={380} stroke="#085041" strokeWidth={3} opacity={0.15} />
    <line x1={770} y1={420} x2={860} y2={360} stroke="#085041" strokeWidth={3} opacity={0.15} />
    <line x1={770} y1={500} x2={700} y2={480} stroke="#085041" strokeWidth={2} opacity={0.15} />

    {/* Foliage */}
    {[[770,380,22],[730,360,18],[810,370,16],[680,360,15],[860,340,14],[750,330,13],[700,400,12],[840,380,11]].map(
      ([x,y,r],i) => <circle key={`f${i}`} cx={x} cy={y} r={r} fill="#085041" opacity={0.12} />
    )}
    {[[760,350,10],[820,350,8]].map(
      ([x,y,r],i) => <circle key={`fl${i}`} cx={x} cy={y} r={r} fill="#5DCAA5" opacity={0.10} />
    )}

    {/* Lotus pond */}
    <ellipse cx={170} cy={770} rx={150} ry={50} fill="#85B7EB" opacity={0.08} />
    {/* Lotus flowers */}
    {[0,1,2,3,4].map(i => {
      const a = i * Math.PI * 2 / 5 - Math.PI / 2
      return <circle key={`l1${i}`} cx={120 + Math.cos(a)*7} cy={750 + Math.sin(a)*7} r={6} fill="#ED93B1" opacity={0.16} />
    })}
    <circle cx={120} cy={750} r={4} fill="#F5C4D8" opacity={0.12} />
    {[0,1,2,3,4].map(i => {
      const a = i * Math.PI * 2 / 5 - Math.PI / 2
      return <circle key={`l2${i}`} cx={220 + Math.cos(a)*5.5} cy={770 + Math.sin(a)*5.5} r={5} fill="#ED93B1" opacity={0.16} />
    })}

    {/* Lily pads */}
    {[[80,780,10],[180,800,8],[280,760,9]].map(
      ([x,y,r],i) => <circle key={i} cx={x} cy={y} r={r} fill="#085041" opacity={0.09} />
    )}
  </>,

  /* ── Interactions: Bridge, Pagoda, Lanterns, Willow ──────────────── */
  interactions: () => <>
    {/* River */}
    <path d={wave(840, 5, 150, 0).replace('L 1000 1000 L 0 1000 Z', '') + ` L 1000 900 ` + Array.from({length:126}, (_,i) => {
      const x = 1000 - i*8; return `L ${x} ${900 + Math.sin(x * Math.PI / 130) * 4}`
    }).join(' ') + ' Z'} fill="#85B7EB" opacity={0.09} />

    {/* Arched bridge */}
    <path d="M 150 880 Q 350 720 550 880" fill="none" stroke="#712B13" strokeWidth={5} opacity={0.12} />
    <path d="M 170 865 Q 350 710 530 865" fill="none" stroke="#712B13" strokeWidth={2} opacity={0.08} />
    {[220,280,350,420,480].map(x => {
      const h = 1 - 4*(x/1000-0.35)**2
      const top = Math.min(880, 880 - h*120)
      return <line key={x} x1={x} y1={top} x2={x} y2={880} stroke="#712B13" strokeWidth={1.5} opacity={0.07} />
    })}

    {/* Pagoda */}
    <line x1={850} y1={620} x2={850} y2={500} stroke="#712B13" strokeWidth={4} opacity={0.12} />
    {[0,1,2].map(i => {
      const y = 500+i*35; const tw = 35-i*6
      return <g key={i}>
        <line x1={850-tw} y1={y} x2={850+tw} y2={y} stroke="#712B13" strokeWidth={3} opacity={0.12} />
        <path d={`M ${850-tw-8} ${y+2} Q ${850} ${y+8} ${850+tw+8} ${y+2}`} fill="none" stroke="#712B13" strokeWidth={1.5} opacity={0.08} />
      </g>
    })}
    <line x1={850} y1={500} x2={850} y2={480} stroke="#712B13" strokeWidth={2} opacity={0.12} />

    {/* Hanging lanterns */}
    <line x1={50} y1={60} x2={600} y2={40} stroke="#712B13" strokeWidth={1} opacity={0.06} />
    {[120,250,380,500].map(x =>
      <g key={x}>
        <line x1={x} y1={50} x2={x} y2={62} stroke="#712B13" strokeWidth={0.8} opacity={0.06} />
        <ellipse cx={x} cy={69} rx={5} ry={7} fill="#F0997B" opacity={0.14} />
      </g>
    )}

    {/* Willow branches */}
    {[[40,50,80,300],[60,40,120,280],[30,60,20,320],[70,30,150,250]].map(([x1,y1,x2,y2],i) =>
      <path key={i} d={`M ${x1} ${y1} C ${x1+10} ${(y1+y2)/2} ${x2-5} ${y2-20} ${x2} ${y2}`}
        fill="none" stroke="#085041" strokeWidth={1.5} opacity={0.09} />
    )}

    {/* Petals */}
    {[[930,100,7],[880,140,5],[960,60,4]].map(([x,y,r],i) => <circle key={i} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.16} />)}
  </>,

  /* ── Chores: Raked Garden, Stone Arrangements, Fence ─────────────── */
  chores: () => <>
    {/* Bamboo fence */}
    {Array.from({length:21}, (_,i) => i*50).map(x =>
      <line key={x} x1={x} y1={40} x2={x} y2={140} stroke="#444441" strokeWidth={3} opacity={0.08} />
    )}
    <line x1={0} y1={70} x2={1000} y2={70} stroke="#444441" strokeWidth={2} opacity={0.08} />
    <line x1={0} y1={110} x2={1000} y2={110} stroke="#444441" strokeWidth={2} opacity={0.08} />

    {/* Raked sand */}
    {Array.from({length:19}, (_,i) => 550+i*14).map(y =>
      <line key={y} x1={0} y1={y} x2={1000} y2={y} stroke="#444441" strokeWidth={0.7} opacity={0.06} />
    )}

    {/* Stone group 1 + raked ellipses */}
    <ellipse cx={600} cy={720} rx={20} ry={15} fill="#444441" opacity={0.10} />
    <ellipse cx={618} cy={712} rx={10} ry={8} fill="#444441" opacity={0.08} />
    <ellipse cx={570} cy={714} rx={8} ry={6} fill="#444441" opacity={0.07} />
    {[1,2,3,4,5].map(r =>
      <ellipse key={r} cx={600} cy={720} rx={20+r*12} ry={15+r*8} fill="none" stroke="#444441" strokeWidth={0.7} opacity={0.06} />
    )}

    {/* Stone group 2 */}
    <ellipse cx={200} cy={800} rx={12} ry={10} fill="#444441" opacity={0.10} />
    <circle cx={216} cy={800} r={7} fill="#444441" opacity={0.07} />
    {[1,2,3].map(r =>
      <ellipse key={r} cx={200} cy={800} rx={12+r*10} ry={10+r*7} fill="none" stroke="#444441" strokeWidth={0.7} opacity={0.06} />
    )}

    {/* Moss */}
    <ellipse cx={855} cy={670} rx={75} ry={20} fill="#444441" opacity={0.05} />

    {/* Broom */}
    <line x1={920} y1={880} x2={880} y2={700} stroke="#444441" strokeWidth={3} opacity={0.08} />
    {[-3,-2,-1,0,1,2,3].map(i =>
      <line key={i} x1={920} y1={880} x2={920+i*4} y2={950} stroke="#444441" strokeWidth={1.5} opacity={0.08} />
    )}
  </>,

  /* ── Hobbies: Origami Cranes, Fan, Ink Splash ────────────────────── */
  hobbies: () => <>
    {/* Origami cranes in V */}
    {[[500,60,24,0],[380,100,20,-0.15],[620,100,20,0.15],[280,150,16,-0.25],[720,150,16,0.25]].map(([cx,cy,sz,tilt],i) =>
      <g key={i} opacity={0.12}>
        <path d={`M ${cx} ${cy-sz*0.1} L ${cx-sz*(1+tilt)} ${cy-sz*0.7} L ${cx-sz*0.3} ${cy+sz*0.05}
          M ${cx} ${cy-sz*0.1} L ${cx+sz*(1-tilt)} ${cy-sz*0.7} L ${cx+sz*0.3} ${cy+sz*0.05}
          M ${cx-sz*0.1} ${cy} L ${cx-sz*0.4} ${cy+sz*0.4}
          M ${cx+sz*0.1} ${cy-sz*0.2} L ${cx+sz*0.35} ${cy-sz*0.5}`}
          fill="none" stroke="#72243E" strokeWidth={1.5} />
      </g>
    )}

    {/* Paper fan */}
    {Array.from({length:9}, (_,i) => {
      const a = -Math.PI/2 - Math.PI/6 + i*(Math.PI/3)/8
      return <line key={i} x1={120} y1={880} x2={120+Math.cos(a)*60} y2={880+Math.sin(a)*60}
        stroke="#72243E" strokeWidth={1} opacity={0.10} />
    })}
    <path d={`M ${120+Math.cos(-Math.PI/2-Math.PI/6)*60} ${880+Math.sin(-Math.PI/2-Math.PI/6)*60} ` +
      Array.from({length:21}, (_,i) => {
        const a = -Math.PI/2 - Math.PI/6 + i*(Math.PI/3)/20
        return `L ${120+Math.cos(a)*60} ${880+Math.sin(a)*60}`
      }).join(' ')}
      fill="none" stroke="#72243E" strokeWidth={2} opacity={0.10} />

    {/* Ink brush stroke */}
    <path d="M 600 850 C 700 800 800 820 920 780" fill="none" stroke="#72243E" strokeWidth={8} opacity={0.09} />
    {[[880,750,4],[920,800,3],[850,820,2],[950,760,2.5]].map(([x,y,r],i) =>
      <circle key={i} cx={x} cy={y} r={r} fill="#72243E" opacity={0.09} />
    )}

    {/* Cherry blossoms */}
    {[[900,40,7],[850,80,5],[60,450,6],[100,500,4]].map(([x,y,r],i) =>
      <circle key={i} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.16} />
    )}

    {/* Washi paper */}
    <rect x={350} y={550} width={50} height={65} rx={2} fill="none" stroke="#72243E" strokeWidth={1} opacity={0.05} />
    <rect x={400} y={500} width={45} height={60} rx={2} fill="none" stroke="#72243E" strokeWidth={1} opacity={0.05} />
  </>,

  /* ── Ideas: Enso Circle, Scroll, Constellations, Lantern ─────────── */
  ideas: () => {
    // Enso circle path (85% arc)
    const ensoPts = Array.from({length:86}, (_,i) => {
      const a = -Math.PI*0.1 + i*(2*Math.PI*0.85)/85
      return `${550 + Math.cos(a)*220} ${400 + Math.sin(a)*220}`
    })
    return <>
      {/* Enso */}
      <path d={`M ${ensoPts[0]} ` + ensoPts.slice(1).map(p => `L ${p}`).join(' ')}
        fill="none" stroke="#2A3A6B" strokeWidth={6} opacity={0.14} />

      {/* Scroll */}
      <rect x={720} y={50} width={220} height={250} rx={4} fill="#2A3A6B" opacity={0.08} />
      <rect x={700} y={40} width={260} height={8} rx={4} fill="#2A3A6B" opacity={0.12} />
      <rect x={700} y={295} width={260} height={8} rx={4} fill="#2A3A6B" opacity={0.12} />
      {[0,1,2,3,4,5].map(i =>
        <line key={i} x1={750} y1={80+i*35} x2={900} y2={80+i*35} stroke="#2A3A6B" strokeWidth={1} opacity={0.06} />
      )}

      {/* Constellation 1 */}
      {[[80,120],[150,80],[220,140],[180,200],[100,180]].map(([x,y],i,arr) => <g key={i}>
        <circle cx={x} cy={y} r={3} fill="#2A3A6B" opacity={0.18} />
        {i < arr.length-1 && <line x1={x} y1={y} x2={arr[i+1][0]} y2={arr[i+1][1]} stroke="#2A3A6B" strokeWidth={1} opacity={0.08} />}
        {i === arr.length-1 && <line x1={x} y1={y} x2={arr[0][0]} y2={arr[0][1]} stroke="#2A3A6B" strokeWidth={1} opacity={0.08} />}
      </g>)}

      {/* Constellation 2 */}
      {[[350,50],[420,100],[400,180]].map(([x,y],i,arr) => <g key={`c2${i}`}>
        <circle cx={x} cy={y} r={2.5} fill="#2A3A6B" opacity={0.18} />
        {i < arr.length-1 && <line x1={x} y1={y} x2={arr[i+1][0]} y2={arr[i+1][1]} stroke="#2A3A6B" strokeWidth={1} opacity={0.08} />}
      </g>)}

      {/* Floating lantern */}
      <ellipse cx={150} cy={796} rx={14} ry={18} fill="#F5E5C4" opacity={0.15} />
      <circle cx={150} cy={814} r={28} fill="#F5E5C4" opacity={0.08} />
      <circle cx={150} cy={808} r={4} fill="#F0997B" opacity={0.12} />
    </>
  },

  /* ── Cycle: Moon Phases, Waves, Floral Wreath ────────────────────── */
  cycle: () => {
    const sp = 1000/7
    const wr = 200 // wreath radius
    return <>
      {/* Moon phases */}
      <circle cx={sp} cy={100} r={14} fill="none" stroke="#7A2040" strokeWidth={1.5} opacity={0.16} />
      <circle cx={sp*2} cy={100} r={14} fill="#7A2040" opacity={0.16} />
      <circle cx={sp*2+6} cy={100} r={11} fill="#F0C8D0" opacity={0.9} />
      <circle cx={sp*3} cy={100} r={14} fill="#7A2040" opacity={0.16} />
      <circle cx={sp*3+14} cy={100} r={14} fill="#F0C8D0" opacity={0.9} />
      <circle cx={sp*4} cy={100} r={14} fill="#7A2040" opacity={0.16} />
      <circle cx={sp*4} cy={100} r={20} fill="#F5C4D8" opacity={0.10} />
      <circle cx={sp*5} cy={100} r={14} fill="#7A2040" opacity={0.16} />
      <circle cx={sp*5-14} cy={100} r={14} fill="#F0C8D0" opacity={0.9} />
      <circle cx={sp*6} cy={100} r={14} fill="#7A2040" opacity={0.16} />
      <circle cx={sp*6-6} cy={100} r={11} fill="#F0C8D0" opacity={0.9} />

      {/* Waves */}
      {[0,1,2].map(i =>
        <path key={i} d={wave(820+i*50, 10, 90, i*50)} fill="#7A2040" opacity={0.08} />
      )}

      {/* Floral wreath */}
      <circle cx={500} cy={480} r={wr} fill="none" stroke="#7A2040" strokeWidth={2} opacity={0.08} />
      {Array.from({length:12}, (_,i) => {
        const a = i*Math.PI*2/12
        const fx = 500 + Math.cos(a)*wr
        const fy = 480 + Math.sin(a)*wr
        const la = a + Math.PI/8
        return <g key={i}>
          <circle cx={fx} cy={fy} r={6} fill="#ED93B1" opacity={0.16} />
          <circle cx={fx+Math.cos(la)*10} cy={fy+Math.sin(la)*10} r={4} fill="#085041" opacity={0.08} />
        </g>
      })}

      {/* Ribbon */}
      <path d={`M ${sp*4} ${114} C 450 200 550 280 500 ${480-wr}`} fill="none" stroke="#7A2040" strokeWidth={2} opacity={0.07} />

      {/* Rose petals */}
      {[[80,300,5],[880,350,6],[120,600,4],[850,550,5],[750,700,4],[200,720,3]].map(([x,y,r],i) =>
        <circle key={i} cx={x} cy={y} r={r} fill="#ED93B1" opacity={0.13} />
      )}
    </>
  },

  /* ── Bad Habits: Smoke, Bottles, Embers, Wilted Leaves ───────────── */
  badhabits: () => <>
    {/* Dusky wash */}
    <circle cx={200} cy={220} r={320} fill="#4A2538" opacity={0.08} />
    <circle cx={820} cy={700} r={280} fill="#6B2A5E" opacity={0.07} />
    <circle cx={600} cy={400} r={220} fill="#8A3A4F" opacity={0.06} />

    {/* Smoke plumes (tobacco) */}
    {[150, 400, 720].map((x, i) => (
      <path key={`sm${i}`}
        d={`M ${x} 900 C ${x - 15} 820 ${x + 18} 740 ${x - 10} 660 C ${x - 25} 580 ${x + 15} 500 ${x} 420 C ${x - 18} 340 ${x + 12} 260 ${x - 5} 180`}
        fill="none" stroke="#6B5562" strokeWidth={14 - i * 2} opacity={0.09 - i * 0.01} strokeLinecap="round" />
    ))}
    {[150, 400, 720].map((x, i) => (
      <path key={`sm2${i}`}
        d={`M ${x + 10} 860 C ${x + 30} 780 ${x - 10} 700 ${x + 20} 620 C ${x + 35} 540 ${x - 5} 460 ${x + 15} 380`}
        fill="none" stroke="#6B5562" strokeWidth={6} opacity={0.07} strokeLinecap="round" />
    ))}

    {/* Cigarette with glowing ember */}
    <line x1={60} y1={940} x2={180} y2={900} stroke="#F5EEDD" strokeWidth={6} opacity={0.22} strokeLinecap="round" />
    <line x1={60} y1={940} x2={95} y2={928} stroke="#C9A46B" strokeWidth={6} opacity={0.25} strokeLinecap="round" />
    <circle cx={180} cy={900} r={7} fill="#F0997B" opacity={0.5} />
    <circle cx={180} cy={900} r={14} fill="#F0997B" opacity={0.15} />

    {/* Wine bottle silhouette */}
    <rect x={870} y={420} width={34} height={120} rx={6} fill="#4A2538" opacity={0.22} />
    <rect x={882} y={370} width={10} height={55} fill="#4A2538" opacity={0.22} />
    <rect x={878} y={360} width={18} height={12} rx={2} fill="#2A1020" opacity={0.28} />
    <ellipse cx={887} cy={480} rx={14} ry={5} fill="#F5E5C4" opacity={0.15} />

    {/* Wine glass */}
    <path d="M 790 480 Q 790 540 820 560 Q 850 540 850 480 Z" fill="#7A2040" opacity={0.20} />
    <line x1={820} y1={560} x2={820} y2={620} stroke="#7A2040" strokeWidth={2.5} opacity={0.20} />
    <ellipse cx={820} cy={622} rx={22} ry={3} fill="#7A2040" opacity={0.20} />
    <ellipse cx={820} cy={500} rx={22} ry={5} fill="#C73E60" opacity={0.30} />

    {/* Wilted cannabis leaves */}
    {[[320, 680, -0.4], [520, 780, 0.3], [680, 620, -0.2]].map(([cx, cy, tilt], i) => (
      <g key={`leaf${i}`} transform={`rotate(${(tilt as number) * 30} ${cx} ${cy})`} opacity={0.16}>
        {[0, 1, 2, 3, 4, 5, 6].map(k => {
          const a = -Math.PI / 2 + (k - 3) * 0.35
          const len = 28 - Math.abs(k - 3) * 5
          const ex = (cx as number) + Math.cos(a) * len
          const ey = (cy as number) + Math.sin(a) * len
          return <line key={k} x1={cx as number} y1={cy as number} x2={ex} y2={ey} stroke="#27500A" strokeWidth={3} strokeLinecap="round" />
        })}
        <circle cx={cx as number} cy={cy as number} r={3} fill="#27500A" />
      </g>
    ))}

    {/* Broken clock hands / scattered ash dots */}
    {[[120, 140, 3], [220, 80, 2], [320, 180, 2.5], [460, 120, 3], [580, 60, 2], [660, 180, 2.5], [440, 260, 2]].map(
      ([x, y, r], i) => <circle key={`ash${i}`} cx={x} cy={y} r={r} fill="#6B5562" opacity={0.22} />
    )}

    {/* Drip / fall lines */}
    <line x1={100} y1={340} x2={100} y2={420} stroke="#4A2538" strokeWidth={1.5} opacity={0.12} />
    <line x1={300} y1={400} x2={300} y2={470} stroke="#4A2538" strokeWidth={1.5} opacity={0.10} />
    <line x1={550} y1={300} x2={550} y2={390} stroke="#4A2538" strokeWidth={1.5} opacity={0.10} />

    {/* Faint crescent moon (it's late) */}
    <circle cx={920} cy={120} r={32} fill="#F5E5C4" opacity={0.15} />
    <circle cx={940} cy={110} r={28} fill="#DDC9D4" opacity={0.9} />
  </>
}
