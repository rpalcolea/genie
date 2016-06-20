import Page from './Page';
import TableRow from './components/TableRow';
import ApplicationDetails from './components/ApplicationDetails';

export default class Application extends Page {

  get url() {
    return '/api/v3/applications';
  }

  get dataKey() {
    return 'applicationList';
  }

  get formFields() {
    return [
      {
        label : 'Name',
        name  : 'name',
        value : '',
        type  : 'input',
      }, {
        label : 'Status',
        name  : 'status',
        value : '',
        type  : 'option',
        optionValues : ['', 'ACTIVE', 'DEPRECATED', 'INACTIVE'],
      }, {
        label : 'Tag',
        name  : 'tag',
        value : '',
        type  : 'input',
      }, {
        label : 'Type',
        name  : 'type',
        value : '',
        type  : 'input',
      }, {
        label : 'Sort By',
        name  : 'sort',
        value : '',
        type  : 'select',
        selectFields: ['name', 'status', 'tag'].map(field => (
          {
            value: field,
            label: field,
          }
        )),
      },
    ];
  }

  get searchPath() {
    return 'applications';
  }

  get rowType() {
    return TableRow;
  }

  get tableHeader() {
    return (
      ['Id', 'Name', 'User', 'Status', 'Version', 'Tags', 'Created', 'Updated']
    );
  }

  get detailsTable() {
    return ApplicationDetails;
  }
}
