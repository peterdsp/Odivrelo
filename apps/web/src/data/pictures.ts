/**
 * The photographs of the subject's world that the pages carry.
 *
 * Every one is a real picture from Wikimedia Commons, served from this origin
 * rather than a remote host, because the product tells the reader it contacts
 * no third party. None shows Odivrelo's own work and none is altered, so the
 * share-alike terms are satisfied by naming the title, the creator and the
 * licence wherever the picture appears.
 */
export interface Picture {
  readonly src: string;
  readonly title: string;
  readonly creator: string;
  readonly licence: string;
  readonly licenceHref: string;
  readonly source: string;
  readonly href: string;
}

const COMMONS = 'Wikimedia Commons';

export const TERMINAL_PICTURE: Picture = {
  src: '/images/ktel-ioannina-bus-station-september-2022-01.jpg',
  title: 'KTEL Ioannina Bus Station, September 2022 01',
  creator: 'Calistemon',
  licence: 'CC BY-SA 4.0',
  licenceHref: 'https://creativecommons.org/licenses/by-sa/4.0',
  source: COMMONS,
  href: 'https://commons.wikimedia.org/wiki/File:KTEL_Ioannina_Bus_Station,_September_2022_01.jpg',
};

export const ROAD_PICTURE: Picture = {
  src: '/images/peloponnese-road.jpg',
  title: 'Peloponnese Road',
  creator: 'Magnus.chronicles',
  licence: 'CC BY 4.0',
  licenceHref: 'https://creativecommons.org/licenses/by/4.0',
  source: COMMONS,
  href: 'https://commons.wikimedia.org/wiki/File:Peloponnese_Road.jpg',
};

export const COACH_PICTURE: Picture = {
  src: '/images/ktel-bus-at-bus-stop-in-kallithea.jpg',
  title: 'KTEL Bus at Bus Stop in Kallithea Rhodes 19 July 2016',
  creator: 'Pjotr Mahhonin',
  licence: 'CC BY-SA 4.0',
  licenceHref: 'https://creativecommons.org/licenses/by-sa/4.0',
  source: COMMONS,
  href: 'https://commons.wikimedia.org/wiki/File:KTEL_Bus_at_Bus_Stop_in_Kallithea_Rhodes_19_July_2016.jpg',
};
